package com.stylemirror.feature.realtime.matching

import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.style.StyleFingerprint

/**
 * Snapshot of the user's persona at one fingerprint version (画像 v2).
 *
 * Carries everything CandidateGenerator needs to assemble the prompt:
 *  - [fingerprint] — 6-dim structured summary (UI / 隐私 protection)
 *  - [behaviorRules] — free-text persona rules (the v2 prompt main course).
 *    Empty string for v1 fingerprints predating Migration 2; callers fall
 *    back to the v1 prompt shape in that case.
 *  - [fingerprintVersion] — pass-through key used by CorpusRetriever to look
 *    up corpus samples for the same version.
 */
data class PersonaSnapshot(
    val fingerprint: StyleFingerprint,
    val behaviorRules: String,
    val fingerprintVersion: Int,
)

/**
 * Supplies the current user style fingerprint to the candidate generator.
 *
 * The real implementation (T14) reads the latest [StyleFingerprint] from Room.
 * Until M2, [FakeStyleEngine] returns a deterministic stub so the M1 pipeline
 * can be exercised end-to-end without a real profile.
 *
 * Returns [Outcome.Err] with [DomainError.InsufficientProfile] when no
 * fingerprint is available yet (e.g. fresh install before onboarding).
 */
interface StyleEngine {
    suspend fun getFingerprint(): Outcome<StyleFingerprint, DomainError>

    /**
     * v2 — snapshot bundling fingerprint + behaviorRules + version.
     * Default impl wraps [getFingerprint] with empty behaviorRules so v1
     * implementations keep working until they upgrade.
     */
    suspend fun getSnapshot(): Outcome<PersonaSnapshot, DomainError> =
        when (val r = getFingerprint()) {
            is Outcome.Ok ->
                Outcome.Ok(
                    PersonaSnapshot(
                        fingerprint = r.value,
                        behaviorRules = "",
                        fingerprintVersion = r.value.version,
                    ),
                )
            is Outcome.Err -> r
        }
}
