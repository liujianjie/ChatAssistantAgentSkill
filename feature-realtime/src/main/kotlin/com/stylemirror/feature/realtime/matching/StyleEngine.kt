package com.stylemirror.feature.realtime.matching

import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.style.StyleFingerprint

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
}
