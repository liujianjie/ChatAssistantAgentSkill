package com.stylemirror.core.data.profiling

import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.repository.StyleFingerprintStore
import java.time.Instant

/**
 * Pure-logic export/import of [StyleFingerprintEntity] for P9 (重装/换机存续).
 *
 * Privacy: the exported JSON contains only the 6-dimension structured
 * fingerprint — no raw chat messages, no API keys, no DB rowIds. Users may
 * store the file anywhere they trust (网盘 / 本地 / 文件传输). Files are NOT
 * encrypted on purpose: encryption would require a portable secret which is
 * exactly what we want to avoid.
 *
 * No Android dependencies — callers wrap a SAF Uri + ContentResolver around
 * these functions on their own (see app/SettingsScreen plumbing).
 */
object ProfileExport {
    /**
     * Returns the JSON blob of the latest fingerprint, or null if no profile
     * has ever been built (caller should disable the export button or surface
     * a "do onboarding first" hint).
     *
     * Re-encodes through [FingerprintJson] rather than emitting the raw DB
     * blob: forces schema-rounding so older DB rows export in the current
     * field shape, easing forward migration.
     */
    suspend fun exportLatest(store: StyleFingerprintStore): String? {
        val entity = store.findLatest() ?: return null
        val fp = FingerprintJson.fromJson(entity.fingerprintJson)
        return FingerprintJson.toJsonForExport(fp)
    }

    /**
     * Validates [json] and writes it as a NEW fingerprint version (does not
     * overwrite history — old versions remain in [HistoryScreen] and can be
     * rolled back to).
     *
     * Returns a typed [ImportResult] so the caller can render the right
     * user-facing error message without exception handling at the call site.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    suspend fun importJson(
        store: StyleFingerprintStore,
        json: String,
    ): ImportResult {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return ImportResult.EmptyJson

        val fingerprint =
            try {
                FingerprintJson.fromJson(trimmed)
            } catch (e: Exception) {
                return ImportResult.InvalidJson(reason = e.message ?: e::class.simpleName.orEmpty())
            }

        // Guard against "decoded but obviously not a real fingerprint" — the
        // FpDto constructor accepts {} thanks to default values, so we need a
        // semantic floor. sampleSize > 0 means an actual profiling run produced
        // this; users importing random JSON would land on 0.
        if (fingerprint.sampleSize <= 0) return ImportResult.NotAFingerprint

        val newVersion = store.nextVersion()
        store.insert(
            StyleFingerprintEntity(
                version = newVersion,
                createdAtEpochMs = Instant.now().toEpochMilli(),
                sampleSize = fingerprint.sampleSize,
                partnerScopeId = fingerprint.partnerScope?.value,
                fingerprintJson = FingerprintJson.toJson(fingerprint.copy(version = newVersion)),
            ),
        )
        return ImportResult.Success(newVersion = newVersion, sampleSize = fingerprint.sampleSize)
    }
}

/** Outcome of [ProfileExport.importJson]. */
sealed class ImportResult {
    /** New version written; old versions retained. */
    data class Success(val newVersion: Int, val sampleSize: Int) : ImportResult()

    /** File was empty or whitespace-only. */
    data object EmptyJson : ImportResult()

    /** kotlinx.serialization rejected the payload. */
    data class InvalidJson(val reason: String) : ImportResult()

    /** Parsed but with `sampleSize <= 0` — caller probably picked a non-fingerprint file. */
    data object NotAFingerprint : ImportResult()
}
