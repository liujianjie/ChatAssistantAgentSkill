package com.stylemirror.feature.realtime.matching

import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.style.ApproachStyle
import com.stylemirror.domain.style.AvoidancePatterns
import com.stylemirror.domain.style.DeflectionStrategy
import com.stylemirror.domain.style.Directness
import com.stylemirror.domain.style.EmotionalExpression
import com.stylemirror.domain.style.EmotionalTone
import com.stylemirror.domain.style.FormalityLevel
import com.stylemirror.domain.style.HumorStyle
import com.stylemirror.domain.style.HumorType
import com.stylemirror.domain.style.LinguisticStyle
import com.stylemirror.domain.style.NonNegativeFloat
import com.stylemirror.domain.style.NormalizedScore
import com.stylemirror.domain.style.PacingTraits
import com.stylemirror.domain.style.ResponseDelayTier
import com.stylemirror.domain.style.SensitiveTopicHandling
import com.stylemirror.domain.style.SentencePattern
import com.stylemirror.domain.style.StyleFingerprint
import java.time.Instant

/**
 * Deterministic [StyleEngine] for tests and the M1 candidate pipeline.
 *
 * Returns a fixed stub [StyleFingerprint] that exercises all six dimensions
 * without depending on a real import corpus or a Room database. The score
 * returned for match estimation is always [FIXED_MATCH_SCORE].
 *
 * Inject a custom [fingerprintOverride] in tests that need to assert
 * prompt-builder behavior for specific style configurations.
 */
class FakeStyleEngine(
    private val fingerprintOverride: StyleFingerprint = DEFAULT_FINGERPRINT,
) : StyleEngine {
    override suspend fun getFingerprint(): Outcome<StyleFingerprint, DomainError> = Outcome.Ok(fingerprintOverride)

    companion object {
        const val FIXED_MATCH_SCORE: Float = 0.75f

        val DEFAULT_FINGERPRINT: StyleFingerprint =
            StyleFingerprint(
                version = 1,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                sampleSize = 100,
                partnerScope = null,
                linguistic =
                    LinguisticStyle(
                        formality = FormalityLevel.CASUAL,
                        vocabularyComplexity = NormalizedScore(0.3f),
                        sentencePattern = SentencePattern.SHORT_FRAGMENTED,
                        signaturePhrases = listOf("哈哈", "好的", "嗯嗯"),
                    ),
                emotional =
                    EmotionalExpression(
                        emojiDensityPer100Chars = NonNegativeFloat(2.5f),
                        exclamationFrequency = NormalizedScore(0.4f),
                        tone = EmotionalTone.BALANCED,
                        preferredEmojis = listOf("😄", "👍", "🌙"),
                    ),
                humor =
                    HumorStyle(
                        frequency = NormalizedScore(0.5f),
                        types = setOf(HumorType.OBSERVATIONAL, HumorType.SELF_DEPRECATING),
                    ),
                avoidance =
                    AvoidancePatterns(
                        topicsAvoided = listOf("politics"),
                        hedgingFrequency = NormalizedScore(0.2f),
                        deflectionStrategy = DeflectionStrategy.REDIRECT,
                    ),
                pacing =
                    PacingTraits(
                        avgMessageLengthChars = NonNegativeFloat(25f),
                        avgMessagesPerTurn = NonNegativeFloat(1.5f),
                        responseDelay = ResponseDelayTier.MINUTES,
                    ),
                sensitive =
                    SensitiveTopicHandling(
                        directness = Directness.INDIRECT,
                        approach = ApproachStyle.EMPATHETIC,
                    ),
            )
    }
}
