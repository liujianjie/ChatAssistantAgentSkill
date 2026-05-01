package com.stylemirror.domain.style

/*
 * Six-dimension style fingerprint schema (v1).
 *
 * Each dimension is a concrete data class — never Map<String, Any> — so
 * downstream consumers (LLM prompt builders, UI summaries, similarity scorers)
 * compile-fail when the schema evolves rather than silently dropping fields.
 *
 * Privacy red line: every field on every dimension MUST be derivable from the
 * user's own messages alone. Adding a field that requires "what the other party
 * said" is a SPEC §6.3 red-line violation. See ADR-0001 for the rationale.
 */

/** 1. 语言风格 — vocabulary, formality, signature phrasing. */
data class LinguisticStyle(
    val formality: FormalityLevel,
    val vocabularyComplexity: NormalizedScore,
    val sentencePattern: SentencePattern,
    /** Frequent phrases the user reaches for. Stored verbatim for prompt seeding. */
    val signaturePhrases: List<String>,
) {
    init {
        require(signaturePhrases.size <= MAX_SIGNATURE_PHRASES) {
            "signaturePhrases must not exceed $MAX_SIGNATURE_PHRASES entries"
        }
    }

    companion object {
        const val MAX_SIGNATURE_PHRASES = 30
    }
}

enum class FormalityLevel { CASUAL, NEUTRAL, FORMAL }

enum class SentencePattern { SHORT_FRAGMENTED, MIXED, LONG_STRUCTURED }

/** 2. 情感表达 — emoji density, exclamation, overall tone. */
data class EmotionalExpression(
    val emojiDensityPer100Chars: NonNegativeFloat,
    val exclamationFrequency: NormalizedScore,
    val tone: EmotionalTone,
    val preferredEmojis: List<String>,
) {
    init {
        require(preferredEmojis.size <= MAX_PREFERRED_EMOJIS) {
            "preferredEmojis must not exceed $MAX_PREFERRED_EMOJIS entries"
        }
    }

    companion object {
        const val MAX_PREFERRED_EMOJIS = 20
    }
}

enum class EmotionalTone { RESERVED, BALANCED, EXPRESSIVE }

/** 3. 幽默类型 — frequency and flavours of humour the user employs. */
data class HumorStyle(
    val frequency: NormalizedScore,
    val types: Set<HumorType>,
)

enum class HumorType { NONE, SELF_DEPRECATING, WORDPLAY, OBSERVATIONAL, ABSURDIST, DEADPAN }

/** 4. 回避模式 — how the user pulls back from topics they don't want to engage with. */
data class AvoidancePatterns(
    /** Generic topic labels (e.g. "personal-finance", "politics"). Never names or PII. */
    val topicsAvoided: List<String>,
    val hedgingFrequency: NormalizedScore,
    val deflectionStrategy: DeflectionStrategy,
) {
    init {
        require(topicsAvoided.size <= MAX_AVOIDED_TOPICS) {
            "topicsAvoided must not exceed $MAX_AVOIDED_TOPICS entries"
        }
    }

    companion object {
        const val MAX_AVOIDED_TOPICS = 20
    }
}

enum class DeflectionStrategy { NONE, SILENT, REDIRECT, JOKE }

/** 5. 节奏特征 — message length and reply latency tendencies. */
data class PacingTraits(
    val avgMessageLengthChars: NonNegativeFloat,
    val avgMessagesPerTurn: NonNegativeFloat,
    val responseDelay: ResponseDelayTier,
)

enum class ResponseDelayTier { IMMEDIATE, MINUTES, HOURS, MIXED }

/** 6. 敏感话题处理 — directness vs indirectness, analytical vs empathetic. */
data class SensitiveTopicHandling(
    val directness: Directness,
    val approach: ApproachStyle,
)

enum class Directness { DIRECT, INDIRECT, EVASIVE }

enum class ApproachStyle { ANALYTICAL, EMPATHETIC, PRAGMATIC }

/**
 * A score normalized to the inclusive `[0.0, 1.0]` range. Wrapping in a value
 * class keeps invariant checks at construction time and stops accidental
 * propagation of out-of-range scores from heuristic implementations.
 */
@JvmInline
value class NormalizedScore(val value: Float) {
    init {
        require(value in MIN..MAX) { "NormalizedScore must be in [$MIN, $MAX], was $value" }
    }

    companion object {
        const val MIN = 0.0f
        const val MAX = 1.0f
        val ZERO = NormalizedScore(0.0f)
        val ONE = NormalizedScore(1.0f)
    }
}

/** A non-negative float — used for unbounded magnitudes like char-count averages. */
@JvmInline
value class NonNegativeFloat(val value: Float) {
    init {
        require(value >= 0.0f && !value.isNaN()) { "NonNegativeFloat must be >= 0, was $value" }
    }
}
