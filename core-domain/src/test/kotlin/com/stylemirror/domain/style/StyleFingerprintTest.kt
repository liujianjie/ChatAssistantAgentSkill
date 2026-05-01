package com.stylemirror.domain.style

import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.conversation.MessageId
import com.stylemirror.domain.conversation.PartnerId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class StyleFingerprintTest : StringSpec({

    "NormalizedScore accepts boundary values" {
        NormalizedScore(0.0f).value shouldBe 0.0f
        NormalizedScore(1.0f).value shouldBe 1.0f
        NormalizedScore(0.5f).value shouldBe 0.5f
    }

    "NormalizedScore rejects out-of-range" {
        shouldThrow<IllegalArgumentException> { NormalizedScore(-0.01f) }
        shouldThrow<IllegalArgumentException> { NormalizedScore(1.01f) }
        shouldThrow<IllegalArgumentException> { NormalizedScore(Float.NaN) }
    }

    "NonNegativeFloat accepts zero and positive" {
        NonNegativeFloat(0.0f).value shouldBe 0.0f
        NonNegativeFloat(42.5f).value shouldBe 42.5f
    }

    "NonNegativeFloat rejects negative and NaN" {
        shouldThrow<IllegalArgumentException> { NonNegativeFloat(-0.01f) }
        shouldThrow<IllegalArgumentException> { NonNegativeFloat(Float.NaN) }
    }

    "LinguisticStyle caps signature phrases at 30" {
        shouldThrow<IllegalArgumentException> {
            LinguisticStyle(
                formality = FormalityLevel.NEUTRAL,
                vocabularyComplexity = NormalizedScore.ZERO,
                sentencePattern = SentencePattern.MIXED,
                signaturePhrases = List(31) { "phrase-$it" },
            )
        }
    }

    "EmotionalExpression caps preferred emojis at 20" {
        shouldThrow<IllegalArgumentException> {
            EmotionalExpression(
                emojiDensityPer100Chars = NonNegativeFloat(0f),
                exclamationFrequency = NormalizedScore.ZERO,
                tone = EmotionalTone.BALANCED,
                preferredEmojis = List(21) { "x" },
            )
        }
    }

    "AvoidancePatterns caps topics at 20" {
        shouldThrow<IllegalArgumentException> {
            AvoidancePatterns(
                topicsAvoided = List(21) { "topic-$it" },
                hedgingFrequency = NormalizedScore.ZERO,
                deflectionStrategy = DeflectionStrategy.NONE,
            )
        }
    }

    "StyleFingerprint enforces version >= 1" {
        shouldThrow<IllegalArgumentException> { fixture(version = 0) }
        shouldThrow<IllegalArgumentException> { fixture(version = -1) }
        fixture(version = 1).version shouldBe 1
    }

    "StyleFingerprint enforces sampleSize >= 0" {
        shouldThrow<IllegalArgumentException> { fixture(sampleSize = -1) }
        fixture(sampleSize = 0).sampleSize shouldBe 0
    }

    "StyleFingerprint partnerScope is optional" {
        fixture(partnerScope = null).partnerScope shouldBe null
        fixture(partnerScope = PartnerId("p")).partnerScope shouldBe PartnerId("p")
    }

    "FingerprintAggregator only accepts Message.Mine — compile-time isolation smoke" {
        // This test exists to fail compilation if the aggregator signature drifts.
        val aggregator = FingerprintAggregator { mine -> fixture(sampleSize = mine.size) }
        val mine =
            listOf(
                Message.Mine(MessageId("m1"), "hi", Instant.parse("2025-01-01T00:00:00Z")),
            )
        aggregator.aggregate(mine).sampleSize shouldBe 1
    }
})

private fun fixture(
    version: Int = 1,
    sampleSize: Int = 100,
    partnerScope: PartnerId? = null,
): StyleFingerprint =
    StyleFingerprint(
        version = version,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        sampleSize = sampleSize,
        partnerScope = partnerScope,
        linguistic =
            LinguisticStyle(
                formality = FormalityLevel.NEUTRAL,
                vocabularyComplexity = NormalizedScore(0.5f),
                sentencePattern = SentencePattern.MIXED,
                signaturePhrases = listOf("好的", "嗯"),
            ),
        emotional =
            EmotionalExpression(
                emojiDensityPer100Chars = NonNegativeFloat(1.2f),
                exclamationFrequency = NormalizedScore(0.1f),
                tone = EmotionalTone.BALANCED,
                preferredEmojis = listOf("😂"),
            ),
        humor =
            HumorStyle(
                frequency = NormalizedScore(0.3f),
                types = setOf(HumorType.SELF_DEPRECATING),
            ),
        avoidance =
            AvoidancePatterns(
                topicsAvoided = listOf("politics"),
                hedgingFrequency = NormalizedScore(0.2f),
                deflectionStrategy = DeflectionStrategy.JOKE,
            ),
        pacing =
            PacingTraits(
                avgMessageLengthChars = NonNegativeFloat(15f),
                avgMessagesPerTurn = NonNegativeFloat(1.4f),
                responseDelay = ResponseDelayTier.MINUTES,
            ),
        sensitive =
            SensitiveTopicHandling(
                directness = Directness.INDIRECT,
                approach = ApproachStyle.EMPATHETIC,
            ),
    )
