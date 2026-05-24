package com.stylemirror.app.onboarding

import com.stylemirror.domain.style.ApproachStyle
import com.stylemirror.domain.style.DeflectionStrategy
import com.stylemirror.domain.style.Directness
import com.stylemirror.domain.style.EmotionalTone
import com.stylemirror.domain.style.FormalityLevel
import com.stylemirror.domain.style.HumorType
import com.stylemirror.domain.style.ResponseDelayTier
import com.stylemirror.domain.style.SentencePattern
import com.stylemirror.domain.style.StyleFingerprint

/**
 * Six-dimension human-readable Chinese summary of a [StyleFingerprint].
 *
 * Used by onboarding's "review" step so the user can sanity-check the
 * generated profile before entering the main app. Each dimension produces
 * one short sentence — keep it readable, not exhaustive.
 */
data class StyleFingerprintSummary(
    val sampleCount: Int,
    val linguistic: String,
    val emotional: String,
    val humor: String,
    val avoidance: String,
    val pacing: String,
    val sensitive: String,
) {
    companion object {
        fun of(
            fp: StyleFingerprint,
            sampleCount: Int,
        ): StyleFingerprintSummary =
            StyleFingerprintSummary(
                sampleCount = sampleCount,
                linguistic = describeLinguistic(fp),
                emotional = describeEmotional(fp),
                humor = describeHumor(fp),
                avoidance = describeAvoidance(fp),
                pacing = describePacing(fp),
                sensitive = describeSensitive(fp),
            )

        private fun describeLinguistic(fp: StyleFingerprint): String {
            val formality =
                when (fp.linguistic.formality) {
                    FormalityLevel.CASUAL -> "随意口语"
                    FormalityLevel.NEUTRAL -> "中性自然"
                    FormalityLevel.FORMAL -> "偏正式"
                }
            val pattern =
                when (fp.linguistic.sentencePattern) {
                    SentencePattern.SHORT_FRAGMENTED -> "短句碎句"
                    SentencePattern.MIXED -> "长短混合"
                    SentencePattern.LONG_STRUCTURED -> "长句结构化"
                }
            val phrases =
                fp.linguistic.signaturePhrases.take(MAX_INLINE_PHRASES).joinToString("、")
            val phrasesPart =
                if (phrases.isBlank()) "" else "，常用：$phrases"
            return "$formality；$pattern$phrasesPart"
        }

        private fun describeEmotional(fp: StyleFingerprint): String {
            val tone =
                when (fp.emotional.tone) {
                    EmotionalTone.RESERVED -> "克制"
                    EmotionalTone.BALANCED -> "平衡"
                    EmotionalTone.EXPRESSIVE -> "外放"
                }
            val emoji = "%.1f".format(fp.emotional.emojiDensityPer100Chars.value)
            val emojis =
                fp.emotional.preferredEmojis.take(MAX_INLINE_EMOJIS).joinToString("")
            val emojisPart = if (emojis.isBlank()) "" else "，偏好：$emojis"
            return "$tone；每 100 字约 $emoji 个 emoji$emojisPart"
        }

        private fun describeHumor(fp: StyleFingerprint): String {
            val freq = ratioWord(fp.humor.frequency.value)
            val labels =
                fp.humor.types.map {
                    when (it) {
                        HumorType.NONE -> "无"
                        HumorType.SELF_DEPRECATING -> "自嘲"
                        HumorType.WORDPLAY -> "文字游戏"
                        HumorType.OBSERVATIONAL -> "观察式"
                        HumorType.ABSURDIST -> "荒诞"
                        HumorType.DEADPAN -> "冷面"
                    }
                }.joinToString("、")
            return "$freq；类型：$labels"
        }

        private fun describeAvoidance(fp: StyleFingerprint): String {
            val deflect =
                when (fp.avoidance.deflectionStrategy) {
                    DeflectionStrategy.NONE -> "正面回应"
                    DeflectionStrategy.SILENT -> "沉默"
                    DeflectionStrategy.REDIRECT -> "转移话题"
                    DeflectionStrategy.JOKE -> "调侃化解"
                }
            val hedging = ratioWord(fp.avoidance.hedgingFrequency.value)
            val topics = fp.avoidance.topicsAvoided.joinToString("、")
            val topicsPart = if (topics.isBlank()) "" else "，回避：$topics"
            return "$deflect；模糊措辞 $hedging$topicsPart"
        }

        private fun describePacing(fp: StyleFingerprint): String {
            val avgLen = "%.1f".format(fp.pacing.avgMessageLengthChars.value)
            val avgPerTurn = "%.1f".format(fp.pacing.avgMessagesPerTurn.value)
            val delay =
                when (fp.pacing.responseDelay) {
                    ResponseDelayTier.IMMEDIATE -> "秒回"
                    ResponseDelayTier.MINUTES -> "数分钟"
                    ResponseDelayTier.HOURS -> "数小时"
                    ResponseDelayTier.MIXED -> "时快时慢"
                }
            return "平均 $avgLen 字/条；每轮 $avgPerTurn 条；回复速度：$delay"
        }

        private fun describeSensitive(fp: StyleFingerprint): String {
            val direct =
                when (fp.sensitive.directness) {
                    Directness.DIRECT -> "直接"
                    Directness.INDIRECT -> "委婉"
                    Directness.EVASIVE -> "回避"
                }
            val approach =
                when (fp.sensitive.approach) {
                    ApproachStyle.ANALYTICAL -> "分析型"
                    ApproachStyle.EMPATHETIC -> "共情型"
                    ApproachStyle.PRAGMATIC -> "务实型"
                }
            return "$direct；$approach"
        }

        private fun ratioWord(score: Float): String =
            when {
                score < LOW_THRESHOLD -> "低频"
                score < HIGH_THRESHOLD -> "中等"
                else -> "高频"
            }

        private const val MAX_INLINE_PHRASES = 5
        private const val MAX_INLINE_EMOJIS = 8
        private const val LOW_THRESHOLD = 0.25f
        private const val HIGH_THRESHOLD = 0.6f
    }
}
