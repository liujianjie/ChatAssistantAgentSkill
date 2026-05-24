package com.stylemirror.core.data.profiling

import com.stylemirror.domain.conversation.PartnerId
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Converts [StyleFingerprint] to/from a compact JSON string for storage in
 * [com.stylemirror.core.data.db.entity.StyleFingerprintEntity.fingerprintJson]
 * and for parsing LLM responses from [PersonaProfiler].
 *
 * Uses internal [Dto] data classes annotated with [Serializable] so the
 * domain model (core-domain) stays free of serialization dependencies.
 * Unknown fields are silently ignored by `ignoreUnknownKeys = true`.
 */
object FingerprintJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    fun toJson(fp: StyleFingerprint): String = json.encodeToString(fp.toDto())

    fun fromJson(text: String): StyleFingerprint {
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        val jsonStr =
            if (jsonStart >= 0 && jsonEnd > jsonStart) text.substring(jsonStart, jsonEnd + 1) else text
        return json.decodeFromString<FpDto>(jsonStr).toDomain()
    }

    // ---------- DTOs ---------------------------------------------------------

    @Serializable
    private data class FpDto(
        val version: Int = StyleFingerprint.MIN_VERSION,
        val createdAtMs: Long = 0L,
        val sampleSize: Int = 0,
        val partnerScopeId: String? = null,
        val linguistic: LinguisticDto = LinguisticDto(),
        val emotional: EmotionalDto = EmotionalDto(),
        val humor: HumorDto = HumorDto(),
        val avoidance: AvoidanceDto = AvoidanceDto(),
        val pacing: PacingDto = PacingDto(),
        val sensitive: SensitiveDto = SensitiveDto(),
    )

    @Serializable
    private data class LinguisticDto(
        val formality: String = "CASUAL",
        val vocabularyComplexity: Float = 0.3f,
        val sentencePattern: String = "MIXED",
        val signaturePhrases: List<String> = emptyList(),
    )

    @Serializable
    private data class EmotionalDto(
        val emojiDensity: Float = 1.0f,
        val exclamationFrequency: Float = 0.2f,
        val tone: String = "BALANCED",
        val preferredEmojis: List<String> = emptyList(),
    )

    @Serializable
    private data class HumorDto(
        val frequency: Float = 0.3f,
        val types: List<String> = listOf("NONE"),
    )

    @Serializable
    private data class AvoidanceDto(
        val topicsAvoided: List<String> = emptyList(),
        val hedgingFrequency: Float = 0.2f,
        val deflectionStrategy: String = "NONE",
    )

    @Serializable
    private data class PacingDto(
        val avgMessageLength: Float = 20.0f,
        val avgMessagesPerTurn: Float = 1.5f,
        val responseDelay: String = "MINUTES",
    )

    @Serializable
    private data class SensitiveDto(
        val directness: String = "INDIRECT",
        val approach: String = "EMPATHETIC",
    )

    // ---------- Domain ↔ DTO conversions -------------------------------------

    private fun StyleFingerprint.toDto() =
        FpDto(
            version = version,
            createdAtMs = createdAt.toEpochMilli(),
            sampleSize = sampleSize,
            partnerScopeId = partnerScope?.value,
            linguistic =
                LinguisticDto(
                    formality = linguistic.formality.name,
                    vocabularyComplexity = linguistic.vocabularyComplexity.value,
                    sentencePattern = linguistic.sentencePattern.name,
                    signaturePhrases = linguistic.signaturePhrases,
                ),
            emotional =
                EmotionalDto(
                    emojiDensity = emotional.emojiDensityPer100Chars.value,
                    exclamationFrequency = emotional.exclamationFrequency.value,
                    tone = emotional.tone.name,
                    preferredEmojis = emotional.preferredEmojis,
                ),
            humor =
                HumorDto(
                    frequency = humor.frequency.value,
                    types = humor.types.map { it.name },
                ),
            avoidance =
                AvoidanceDto(
                    topicsAvoided = avoidance.topicsAvoided,
                    hedgingFrequency = avoidance.hedgingFrequency.value,
                    deflectionStrategy = avoidance.deflectionStrategy.name,
                ),
            pacing =
                PacingDto(
                    avgMessageLength = pacing.avgMessageLengthChars.value,
                    avgMessagesPerTurn = pacing.avgMessagesPerTurn.value,
                    responseDelay = pacing.responseDelay.name,
                ),
            sensitive =
                SensitiveDto(
                    directness = sensitive.directness.name,
                    approach = sensitive.approach.name,
                ),
        )

    private fun FpDto.toDomain(): StyleFingerprint {
        val humorTypes =
            this.humor.types
                .mapNotNull { runCatching { HumorType.valueOf(it) }.getOrNull() }
                .toSet()
                .ifEmpty { setOf(HumorType.NONE) }
        return StyleFingerprint(
            version = version.coerceAtLeast(StyleFingerprint.MIN_VERSION),
            createdAt = Instant.ofEpochMilli(createdAtMs),
            sampleSize = sampleSize.coerceAtLeast(0),
            partnerScope = partnerScopeId?.let { PartnerId(it) },
            linguistic =
                LinguisticStyle(
                    formality = enumOrDefault(linguistic.formality, FormalityLevel.CASUAL),
                    vocabularyComplexity = NormalizedScore(linguistic.vocabularyComplexity.coerceIn(0f, 1f)),
                    sentencePattern = enumOrDefault(linguistic.sentencePattern, SentencePattern.MIXED),
                    signaturePhrases = linguistic.signaturePhrases.take(LinguisticStyle.MAX_SIGNATURE_PHRASES),
                ),
            emotional =
                EmotionalExpression(
                    emojiDensityPer100Chars = NonNegativeFloat(emotional.emojiDensity.coerceAtLeast(0f)),
                    exclamationFrequency = NormalizedScore(emotional.exclamationFrequency.coerceIn(0f, 1f)),
                    tone = enumOrDefault(emotional.tone, EmotionalTone.BALANCED),
                    preferredEmojis = emotional.preferredEmojis.take(EmotionalExpression.MAX_PREFERRED_EMOJIS),
                ),
            humor =
                HumorStyle(
                    frequency = NormalizedScore(humor.frequency.coerceIn(0f, 1f)),
                    types = humorTypes,
                ),
            avoidance =
                AvoidancePatterns(
                    topicsAvoided = avoidance.topicsAvoided.take(AvoidancePatterns.MAX_AVOIDED_TOPICS),
                    hedgingFrequency = NormalizedScore(avoidance.hedgingFrequency.coerceIn(0f, 1f)),
                    deflectionStrategy = enumOrDefault(avoidance.deflectionStrategy, DeflectionStrategy.NONE),
                ),
            pacing =
                PacingTraits(
                    avgMessageLengthChars = NonNegativeFloat(pacing.avgMessageLength.coerceAtLeast(0f)),
                    avgMessagesPerTurn = NonNegativeFloat(pacing.avgMessagesPerTurn.coerceAtLeast(0f)),
                    responseDelay = enumOrDefault(pacing.responseDelay, ResponseDelayTier.MINUTES),
                ),
            sensitive =
                SensitiveTopicHandling(
                    directness = enumOrDefault(sensitive.directness, Directness.INDIRECT),
                    approach = enumOrDefault(sensitive.approach, ApproachStyle.EMPATHETIC),
                ),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(
        name: String,
        default: T,
    ): T = runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}
