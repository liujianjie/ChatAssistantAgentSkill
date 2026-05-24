package com.stylemirror.feature.realtime.matching

import com.stylemirror.core.data.profiling.FingerprintJson
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.style.StyleFingerprint

/**
 * Production [StyleEngine] backed by the Room database.
 *
 * Reads the latest [StyleFingerprint] from [StyleFingerprintStore].
 * If the table is empty (no profile yet, e.g. fresh install before
 * onboarding), returns [Outcome.Err] with [DomainError.InsufficientProfile]
 * so the candidate generator can show a graceful "build your profile first"
 * message rather than crashing.
 */
class RoomBackedStyleEngine(
    private val repository: StyleFingerprintStore,
) : StyleEngine {
    override suspend fun getFingerprint(): Outcome<StyleFingerprint, DomainError> {
        val entity =
            repository.findLatest()
                ?: return Outcome.Err(
                    DomainError.InsufficientProfile(
                        collectedSamples = 0,
                        required = MIN_SAMPLES_REQUIRED,
                    ),
                )
        return runCatching { FingerprintJson.fromJson(entity.fingerprintJson) }
            .map { Outcome.Ok(it) }
            .getOrElse { e ->
                Outcome.Err(
                    DomainError.LlmFailure(
                        com.stylemirror.domain.error.LlmFailureReason.INVALID_RESPONSE,
                        cause = e,
                    ),
                )
            }
    }

    companion object {
        private const val MIN_SAMPLES_REQUIRED = 10
    }
}
