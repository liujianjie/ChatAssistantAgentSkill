package com.stylemirror.domain.style

import com.stylemirror.domain.conversation.PartnerId
import java.time.Instant

/**
 * Versioned aggregate of the six style dimensions plus provenance metadata.
 *
 * Versioning is a hard requirement (SPEC §3.4 / plan T20) so feedback signals
 * can be tied back to the exact fingerprint they were generated against and
 * rollback / incremental learning can replay history.
 */
data class StyleFingerprint(
    val version: Int,
    val createdAt: Instant,
    val sampleSize: Int,
    /** Optional: when set, this fingerprint reflects style conditioned on a specific partner. */
    val partnerScope: PartnerId?,
    val linguistic: LinguisticStyle,
    val emotional: EmotionalExpression,
    val humor: HumorStyle,
    val avoidance: AvoidancePatterns,
    val pacing: PacingTraits,
    val sensitive: SensitiveTopicHandling,
) {
    init {
        require(version >= MIN_VERSION) { "version must be >= $MIN_VERSION, was $version" }
        require(sampleSize >= 0) { "sampleSize must be >= 0, was $sampleSize" }
    }

    companion object {
        const val MIN_VERSION = 1
    }
}
