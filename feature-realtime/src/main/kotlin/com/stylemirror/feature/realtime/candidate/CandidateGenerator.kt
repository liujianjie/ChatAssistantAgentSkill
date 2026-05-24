package com.stylemirror.feature.realtime.candidate

import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.error.map
import com.stylemirror.domain.style.StyleFingerprint
import com.stylemirror.feature.realtime.matching.FakeStyleEngine
import com.stylemirror.feature.realtime.matching.PersonaSnapshot
import com.stylemirror.feature.realtime.matching.StyleEngine
import com.stylemirror.feature.realtime.retrieval.CorpusRetriever
import com.stylemirror.infra.llm.LLMProvider

/**
 * Assembles the prompt and calls [llmProvider] for [candidateCount] candidates.
 *
 * **v1 vs v2 prompt selection** (画像 v2 / ADR-0005):
 * - When the snapshot has a non-empty [PersonaSnapshot.behaviorRules] AND a
 *   [corpusRetriever] is wired, the **v2** prompt is used: behaviorRules +
 *   retrieved corpus few-shot + their messages + 6-dim summary.
 * - Otherwise (v1 fingerprints, or no retriever), falls back to the v1
 *   structured-summary-only prompt so users on legacy data still get output.
 *
 * **Privacy invariants**:
 *  1. Only the last [maxTheirMessages] messages from the other party reach
 *     the prompt (hard cap).
 *  2. All "theirs" content is filtered through [PrivacyGuard.redact] for
 *     phone / id-card / bank-card numbers.
 *  3. Corpus samples are passed through [PrivacyGuard.redact] too — even
 *     though [CorpusSampleStore] only accepts [Message.Mine] content, we
 *     re-scrub at the prompt boundary as a defence-in-depth.
 *  4. No raw user message ever leaves the device through the v1 path.
 */
class CandidateGenerator(
    private val llmProvider: LLMProvider,
    private val styleEngine: StyleEngine = FakeStyleEngine(),
    private val corpusRetriever: CorpusRetriever? = null,
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
        val snapshot =
            when (val r = styleEngine.getSnapshot()) {
                is Outcome.Ok -> r.value
                is Outcome.Err -> return r
            }
        val theirRecent = recentTheirsRedacted(context)
        val samples =
            if (snapshot.behaviorRules.isNotBlank() && corpusRetriever != null) {
                corpusRetriever.retrieve(
                    fingerprintVersion = snapshot.fingerprintVersion,
                    theirRecentMessages = theirRecent,
                )
            } else {
                emptyList()
            }
        val prompt = buildPrompt(snapshot, theirRecent, samples)
        return llmProvider.generateCandidates(prompt, n = candidateCount)
            .map { candidates -> attachStyleScore(candidates, snapshot.fingerprint) }
    }

    /**
     * Builds the LLM prompt. v2 path when [snapshot.behaviorRules] is
     * non-empty; otherwise v1 fallback.
     */
    internal fun buildPrompt(
        snapshot: PersonaSnapshot,
        theirRecent: List<String>,
        retrievedSamples: List<CorpusSampleEntity>,
    ): String =
        if (snapshot.behaviorRules.isNotBlank()) {
            buildV2Prompt(snapshot, theirRecent, retrievedSamples)
        } else {
            buildV1Prompt(snapshot.fingerprint, theirRecent)
        }

    private fun buildV2Prompt(
        snapshot: PersonaSnapshot,
        theirRecent: List<String>,
        retrievedSamples: List<CorpusSampleEntity>,
    ): String =
        buildString {
            appendLine("你是一个风格镜像助手。请按下面这位用户的真实说话方式，生成 $candidateCount 条候选回复。")
            appendLine()
            appendLine("【用户的说话规则】")
            appendLine(snapshot.behaviorRules.trim())
            appendLine()
            if (retrievedSamples.isNotEmpty()) {
                appendLine("【用户在不同场景下的真实回复（请仿照这种语感、长度、用词，不要直接复读原话）】")
                retrievedSamples.forEach { s ->
                    appendLine("[${s.scenario}] ${PrivacyGuard.redact(s.text)}")
                }
                appendLine()
            }
            appendLine("【对方最近消息（已脱敏）】")
            appendLine(if (theirRecent.isEmpty()) "（暂无对方消息）" else theirRecent.joinToString("\n"))
            appendLine()
            appendLine("【辅助风格指标】")
            appendLine(buildStyleSummary(snapshot.fingerprint))
            appendLine()
            append("请直接输出 $candidateCount 条候选回复，每条单独一行，不加编号、前缀或解释。")
        }

    private fun buildV1Prompt(
        fingerprint: StyleFingerprint,
        theirRecent: List<String>,
    ): String =
        buildString {
            appendLine("你是一个风格镜像助手。请根据以下用户风格画像，为用户生成 $candidateCount 条候选回复。")
            appendLine()
            appendLine("【用户风格画像】")
            appendLine(buildStyleSummary(fingerprint))
            appendLine()
            appendLine("【对方最近消息（已脱敏）】")
            appendLine(if (theirRecent.isEmpty()) "（暂无对方消息）" else theirRecent.joinToString("\n"))
            appendLine()
            append("请直接输出 $candidateCount 条候选回复，每条单独一行，不加编号、前缀或解释。")
        }

    /** Last [maxTheirMessages] "Theirs" messages, privacy-redacted. */
    internal fun recentTheirsRedacted(context: ConversationContext): List<String> =
        context.theirMessages
            .takeLast(maxTheirMessages)
            .map { msg -> PrivacyGuard.redact((msg as Message.Theirs).content) }

    private fun buildStyleSummary(fp: StyleFingerprint): String =
        buildString {
            appendLine("语言风格：${fp.linguistic.formality} / ${fp.linguistic.sentencePattern}")
            appendLine("情感表达：${fp.emotional.tone}，常用表情：${fp.emotional.preferredEmojis.joinToString()}")
            appendLine("幽默类型：${fp.humor.types.joinToString()}")
            appendLine("回避模式：${fp.avoidance.deflectionStrategy}，敏感话题：${fp.avoidance.topicsAvoided.joinToString()}")
            appendLine("节奏：平均消息长度 ${fp.pacing.avgMessageLengthChars.value} 字符")
            append("敏感话题处理：${fp.sensitive.directness} / ${fp.sensitive.approach}")
        }

    private fun attachStyleScore(
        candidates: List<Candidate>,
        @Suppress("UNUSED_PARAMETER") fingerprint: StyleFingerprint,
    ): List<Candidate> = candidates.map { it.copy(styleMatchScore = FakeStyleEngine.FIXED_MATCH_SCORE) }

    companion object {
        const val DEFAULT_MAX_THEIR_MESSAGES: Int = 10
    }
}
