package com.stylemirror.core.data.profiling

import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.repository.CorpusSampleStore
import com.stylemirror.core.data.repository.StyleFingerprintStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Pure-logic export/import of the user's persona for 重装/换机存续 (P9, P11).
 *
 * v2 schema (画像 v2 / ADR-0005) wraps:
 *   - structured 6-dim fingerprint (`fingerprint`)
 *   - free-text behavior rules (`behaviorRules`)
 *   - tagged corpus samples (`corpusSamples`)
 *
 * Old v1 files (no wrapper, just the bare fingerprint JSON) still import:
 * we detect by absence of the wrapper key and fall back to the v1 path —
 * empty behaviorRules + zero corpus, user is prompted to evolve later.
 *
 * Privacy: file is NOT encrypted (encryption would require a portable secret).
 * The JSON contains only structured profile data + redacted corpus samples
 * — never raw chat history (corpus is verbatim *user* messages only, already
 * passed through PrivacyGuard during profiling).
 *
 * No Android dependencies — callers wrap SAF I/O around these functions.
 */
object ProfileExport {
    private val exportJson =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
            prettyPrint = true
        }

    /**
     * Returns the v2 wrapper JSON of the latest fingerprint, or null when no
     * profile exists. Old v1 rows export with empty behaviorRules + empty
     * corpus list; nothing breaks.
     */
    suspend fun exportLatest(
        store: StyleFingerprintStore,
        corpusStore: CorpusSampleStore,
    ): String? {
        val entity = store.findLatest() ?: return null
        val fp = FingerprintJson.fromJson(entity.fingerprintJson)
        // Use findAllByVersion to preserve user's soft-delete state — when re-
        // imported, deletedAtEpochMs is restored so the user's pruning sticks.
        val corpus = corpusStore.findAllByVersion(entity.version)
        val fingerprintElement: JsonElement = exportJson.parseToJsonElement(FingerprintJson.toJsonForExport(fp))
        val wrapper =
            ExportWrapper(
                fingerprint = fingerprintElement,
                behaviorRules = entity.behaviorRules,
                corpusSamples =
                    corpus.map {
                        CorpusSampleDto(
                            text = it.text,
                            scenario = it.scenario,
                            partnerScopeId = it.partnerScopeId,
                            createdAtEpochMs = it.createdAtEpochMs,
                            deletedAtEpochMs = it.deletedAtEpochMs,
                        )
                    },
            )
        return exportJson.encodeToString(wrapper)
    }

    /**
     * Imports either a v2 wrapper file or a legacy v1 raw fingerprint file
     * and writes a NEW version (history preserved).
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught", "LongMethod")
    suspend fun importJson(
        store: StyleFingerprintStore,
        corpusStore: CorpusSampleStore,
        json: String,
    ): ImportResult {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return ImportResult.EmptyJson

        // Disambiguate v2 (has "fingerprint": {...}) vs v1 (top-level fp keys).
        val isV2Wrapper = looksLikeV2Wrapper(trimmed)
        val (fpJson, behaviorRules, samples) =
            if (isV2Wrapper) {
                val wrapper =
                    try {
                        exportJson.decodeFromString<ExportWrapper>(trimmed)
                    } catch (e: Exception) {
                        return ImportResult.InvalidJson(reason = e.message ?: e::class.simpleName.orEmpty())
                    }
                Triple(
                    exportJson.encodeToString(JsonElement.serializer(), wrapper.fingerprint),
                    wrapper.behaviorRules,
                    wrapper.corpusSamples,
                )
            } else {
                Triple(trimmed, "", emptyList())
            }

        val fingerprint =
            try {
                FingerprintJson.fromJson(fpJson)
            } catch (e: Exception) {
                return ImportResult.InvalidJson(reason = e.message ?: e::class.simpleName.orEmpty())
            }
        if (fingerprint.sampleSize <= 0) return ImportResult.NotAFingerprint

        val newVersion = store.nextVersion()
        val nowMs = Instant.now().toEpochMilli()
        store.insert(
            StyleFingerprintEntity(
                version = newVersion,
                createdAtEpochMs = nowMs,
                sampleSize = fingerprint.sampleSize,
                partnerScopeId = fingerprint.partnerScope?.value,
                fingerprintJson = FingerprintJson.toJson(fingerprint.copy(version = newVersion)),
                behaviorRules = behaviorRules,
            ),
        )
        if (samples.isNotEmpty()) {
            corpusStore.insertAll(
                samples.map { dto ->
                    CorpusSampleEntity(
                        fingerprintVersion = newVersion,
                        partnerScopeId = dto.partnerScopeId,
                        text = dto.text,
                        scenario = dto.scenario,
                        createdAtEpochMs = dto.createdAtEpochMs,
                        deletedAtEpochMs = dto.deletedAtEpochMs,
                    )
                },
            )
        }
        return ImportResult.Success(
            newVersion = newVersion,
            sampleSize = fingerprint.sampleSize,
            corpusSamplesImported = samples.size,
            isV1Legacy = !isV2Wrapper,
        )
    }

    /**
     * Heuristic: v2 wrapper has top-level "fingerprint" object before any
     * 6-dim key. Robust enough for hand-crafted files too.
     */
    internal fun looksLikeV2Wrapper(json: String): Boolean {
        // Quick win: v1 always has "linguistic" near the top; v2 has
        // "fingerprint" before any 6-dim key.
        val fpIdx = json.indexOf("\"fingerprint\"")
        val linIdx = json.indexOf("\"linguistic\"")
        return fpIdx >= 0 && (linIdx < 0 || fpIdx < linIdx)
    }

    @Serializable
    private data class ExportWrapper(
        @SerialName("fingerprint") val fingerprint: JsonElement,
        @SerialName("behaviorRules") val behaviorRules: String = "",
        @SerialName("corpusSamples") val corpusSamples: List<CorpusSampleDto> = emptyList(),
    )

    @Serializable
    private data class CorpusSampleDto(
        @SerialName("text") val text: String,
        @SerialName("scenario") val scenario: String,
        @SerialName("partnerScopeId") val partnerScopeId: String? = null,
        @SerialName("createdAtEpochMs") val createdAtEpochMs: Long = 0L,
        @SerialName("deletedAtEpochMs") val deletedAtEpochMs: Long? = null,
    )
}

/** Outcome of [ProfileExport.importJson]. */
sealed class ImportResult {
    /** New version written; old versions retained. */
    data class Success(
        val newVersion: Int,
        val sampleSize: Int,
        val corpusSamplesImported: Int = 0,
        val isV1Legacy: Boolean = false,
    ) : ImportResult()

    /** File was empty or whitespace-only. */
    data object EmptyJson : ImportResult()

    /** kotlinx.serialization rejected the payload. */
    data class InvalidJson(val reason: String) : ImportResult()

    /** Parsed but with `sampleSize <= 0` — caller probably picked a non-fingerprint file. */
    data object NotAFingerprint : ImportResult()
}
