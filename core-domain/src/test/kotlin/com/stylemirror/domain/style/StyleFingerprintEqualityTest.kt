package com.stylemirror.domain.style

import com.stylemirror.domain.conversation.PartnerId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class StyleFingerprintEqualityTest : StringSpec({

    "StyleFingerprint equality, copy, hashCode" {
        val a = baseFingerprint()
        val b = baseFingerprint()
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
        a shouldNotBe a.copy(version = 2)
        a.copy(partnerScope = PartnerId("q")).partnerScope shouldBe PartnerId("q")
        a.copy(sampleSize = 250).sampleSize shouldBe 250
    }

    "all dimension data classes round-trip via copy()" {
        val f = baseFingerprint()
        f.linguistic.copy(formality = FormalityLevel.FORMAL).formality shouldBe FormalityLevel.FORMAL
        f.emotional.copy(tone = EmotionalTone.RESERVED).tone shouldBe EmotionalTone.RESERVED
        f.humor.copy(types = setOf(HumorType.WORDPLAY)).types shouldBe setOf(HumorType.WORDPLAY)
        f.avoidance.copy(deflectionStrategy = DeflectionStrategy.SILENT).deflectionStrategy shouldBe
            DeflectionStrategy.SILENT
        f.pacing.copy(responseDelay = ResponseDelayTier.HOURS).responseDelay shouldBe ResponseDelayTier.HOURS
        f.sensitive.copy(directness = Directness.DIRECT).directness shouldBe Directness.DIRECT
    }

    "all dimension enums enumerate documented buckets" {
        FormalityLevel.values().toSet() shouldBe
            setOf(
                FormalityLevel.CASUAL,
                FormalityLevel.NEUTRAL,
                FormalityLevel.FORMAL,
            )
        SentencePattern.values().toSet() shouldBe
            setOf(
                SentencePattern.SHORT_FRAGMENTED,
                SentencePattern.MIXED,
                SentencePattern.LONG_STRUCTURED,
            )
        EmotionalTone.values().toSet() shouldBe
            setOf(
                EmotionalTone.RESERVED,
                EmotionalTone.BALANCED,
                EmotionalTone.EXPRESSIVE,
            )
        HumorType.values().toSet() shouldBe
            setOf(
                HumorType.NONE,
                HumorType.SELF_DEPRECATING,
                HumorType.WORDPLAY,
                HumorType.OBSERVATIONAL,
                HumorType.ABSURDIST,
                HumorType.DEADPAN,
            )
        DeflectionStrategy.values().toSet() shouldBe
            setOf(
                DeflectionStrategy.NONE,
                DeflectionStrategy.SILENT,
                DeflectionStrategy.REDIRECT,
                DeflectionStrategy.JOKE,
            )
        ResponseDelayTier.values().toSet() shouldBe
            setOf(
                ResponseDelayTier.IMMEDIATE,
                ResponseDelayTier.MINUTES,
                ResponseDelayTier.HOURS,
                ResponseDelayTier.MIXED,
            )
        Directness.values().toSet() shouldBe
            setOf(
                Directness.DIRECT,
                Directness.INDIRECT,
                Directness.EVASIVE,
            )
        ApproachStyle.values().toSet() shouldBe
            setOf(
                ApproachStyle.ANALYTICAL,
                ApproachStyle.EMPATHETIC,
                ApproachStyle.PRAGMATIC,
            )
    }

    "NormalizedScore companion constants" {
        NormalizedScore.ZERO.value shouldBe 0.0f
        NormalizedScore.ONE.value shouldBe 1.0f
        NormalizedScore.MIN shouldBe 0.0f
        NormalizedScore.MAX shouldBe 1.0f
    }
})

private fun baseFingerprint(): StyleFingerprint =
    StyleFingerprint(
        version = 1,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        sampleSize = 100,
        partnerScope = null,
        linguistic =
            LinguisticStyle(
                formality = FormalityLevel.NEUTRAL,
                vocabularyComplexity = NormalizedScore(0.5f),
                sentencePattern = SentencePattern.MIXED,
                signaturePhrases = listOf("好的"),
            ),
        emotional =
            EmotionalExpression(
                emojiDensityPer100Chars = NonNegativeFloat(1.0f),
                exclamationFrequency = NormalizedScore(0.1f),
                tone = EmotionalTone.BALANCED,
                preferredEmojis = emptyList(),
            ),
        humor = HumorStyle(NormalizedScore(0.3f), setOf(HumorType.SELF_DEPRECATING)),
        avoidance =
            AvoidancePatterns(
                topicsAvoided = emptyList(),
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
