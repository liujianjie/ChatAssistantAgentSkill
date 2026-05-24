package com.stylemirror.feature.realtime.candidate

import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.error.map
import com.stylemirror.domain.style.StyleFingerprint
import com.stylemirror.feature.realtime.matching.FakeStyleEngine
import com.stylemirror.feature.realtime.matching.StyleEngine
import com.stylemirror.infra.llm.LLMProvider

/**
 * Assembles a provider-agnostic prompt from the conversation context and
 * user style fingerprint, calls [llmProvider], and returns up to [candidateCount]
 * enriched [Candidate]s each with a [Candidate.styleMatchScore].
 *
 * Privacy constraints (enforced before the prompt leaves the device):
 *  1. Only the last [maxTheirMessages] messages from the other party are
 *     included — hard cap regardless of conversation length.
 *  2. All "theirs" messages are passed through [PrivacyGuard.redact] to strip
 *     phone numbers, national IDs, and bank card numbers.
 *  3. The user's own messages are **never** sent to the LLM (they are used
 *     only to derive the fingerprint via T14; the fingerprint is embedded as
 *     structured text, not raw chat).
 */
class CandidateGenerator(
    private val llmProvider: LLMProvider,
    private val styleEngine: StyleEngine = FakeStyleEngine(),
    private val candidateCount: Int = LLMProvider.DEFAULT_N,
    /** Maximum number of "theirs" messages included in the LLM prompt. */
    val maxTheirMessages: Int = DEFAULT_MAX_THEIR_MESSAGES,
) {
    suspend fun generate(context: ConversationContext): Outcome<List<Candidate>, DomainError> {
        if (context.messages.isEmpty()) {
            return Outcome.Err(
                DomainError.ImportFailure(reason = com.stylemirror.domain.error.ImportFailureReason.EMPTY_INPUT),
            )
        }
        val fingerprint =
            when (val r = styleEngine.getFingerprint()) {
                is Outcome.Ok -> r.value
                is Outcome.Err -> return r
            }
        val prompt = buildPrompt(context, fingerprint)
        return llmProvider.generateCandidates(prompt, n = candidateCount)
            .map { candidates -> attachStyleScore(candidates, fingerprint) }
    }

    /**
     * Builds a provider-agnostic prompt string. The prompt does NOT contain
     * vendor-specific tokens (system role wrappers, function-call schema, etc.)
     * — those belong inside each [LLMProvider] implementation.
     *
     * Structure:
     *   1. Brief task framing (one sentence)
     *   2. User style summary (all six dimensions as readable key-value pairs)
     *   3. Recent conversation tail — "theirs" only, privacy-filtered, capped
     *      at [maxTheirMessages]
     *   4. Request for exactly [candidateCount] reply suggestions
     */
    internal fun buildPrompt(
        context: ConversationContext,
        fingerprint: StyleFingerprint,
    ): String {
        val theirSnippet = buildTheirSnippet(context)
        val styleSummary = buildStyleSummary(fingerprint)
        return buildString {
            appendLine("你是一个风格镜像助手。请根据以下用户风格画像，为用户生成 $candidateCount 条候选回复。")
            appendLine()
            appendLine("【用户风格画像】")
            appendLine(styleSummary)
            appendLine()
            appendLine("【对方最近消息（已脱敏）】")
            appendLine(theirSnippet)
            appendLine()
            append("请直接输出 $candidateCount 条候选回复，每条单独一行，不加编号、前缀或解释。")
        }
    }

    /** Returns last [maxTheirMessages] "Theirs" messages, privacy-filtered. */
    internal fun buildTheirSnippet(context: ConversationContext): String {
        val recent =
            context.theirMessages
                .takeLast(maxTheirMessages)
        return if (recent.isEmpty()) {
            "（暂无对方消息）"
        } else {
            recent.joinToString(separator = "\n") { msg ->
                PrivacyGuard.redact((msg as Message.Theirs).content)
            }
        }
    }

    private fun buildStyleSummary(fp: StyleFingerprint): String =
        buildString {
            appendLine("语言风格：${fp.linguistic.formality} / ${fp.linguistic.sentencePattern}")
            appendLine("情感表达：${fp.emotional.tone}，常用表情：${fp.emotional.preferredEmojis.joinToString()}")
            appendLine("幽默类型：${fp.humor.types.joinToString()}")
            appendLine("回避模式：${fp.avoidance.deflectionStrategy}，敏感话题：${fp.avoidance.topicsAvoided.joinToString()}")
            appendLine("节奏：平均消息长度 ${fp.pacing.avgMessageLengthChars.value} 字符")
            append("敏感话题处理：${fp.sensitive.directness} / ${fp.sensitive.approach}")
        }

    /**
     * Attaches a [Candidate.styleMatchScore] to each candidate.
     *
     * Until the real scoring algorithm lands in T14, every candidate receives
     * the [FakeStyleEngine.FIXED_MATCH_SCORE] as a placeholder. When a real
     * [StyleEngine] is plugged in, this method can compare the candidate text
     * against the fingerprint and assign a per-candidate score.
     */
    private fun attachStyleScore(
        candidates: List<Candidate>,
        @Suppress("UNUSED_PARAMETER") fingerprint: StyleFingerprint,
    ): List<Candidate> = candidates.map { it.copy(styleMatchScore = FakeStyleEngine.FIXED_MATCH_SCORE) }

    companion object {
        const val DEFAULT_MAX_THEIR_MESSAGES: Int = 10
    }
}
