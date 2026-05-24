package com.stylemirror.feature.realtime.feedback

import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.profiling.FingerprintJson
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.LlmFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.feedback.FeedbackSignal
import com.stylemirror.domain.style.StyleFingerprint
import com.stylemirror.infra.llm.LLMProvider
import java.time.Instant

/**
 * Folds accumulated [FeedbackSignal]s back into a fresh [StyleFingerprint]
 * version, persisting the new version with [StyleFingerprintStore].
 *
 * ## Triggering
 *
 * Caller decides when to invoke (e.g. every N feedback signals, or on
 * a manual user action). This class is stateless — invoke `learn()` and
 * it queries everything it needs from the repositories.
 *
 * ## Privacy red line (compile-time)
 *
 * The LLM payload assembled by [buildPrompt] takes only:
 *   - The current [StyleFingerprint] (already privacy-clean by ADR-0001)
 *   - User adopt counts (numbers, no content)
 *   - User discard counts grouped by reason (numbers, no content)
 *   - The user's own [FeedbackSignal.Modify.editedContent] strings
 *
 * `Theirs` content has no path to the prompt because no [FeedbackSignal]
 * variant carries it. A future contributor cannot break this without
 * adding a new field to [FeedbackSignal] — at which point ADR-0001 and
 * this class would need a coordinated review.
 *
 * ## Versioning & rollback
 *
 * Each successful [learn] inserts a new [StyleFingerprintEntity] with
 * a monotonically increasing version. Earlier versions are NOT deleted
 * (UI offers a manual rollback by writing the older entity's JSON into
 * a new entity with a newer version number — this class doesn't roll
 * back; the ViewModel does).
 *
 * @param minSignalsToLearn If fewer than this many signals are available,
 *   `learn` returns [Outcome.Err(InsufficientProfile)] without burning an
 *   LLM call.
 */
class IncrementalLearner(
    private val llmProvider: LLMProvider,
    private val fingerprintStore: StyleFingerprintStore,
    private val feedbackProvider: FeedbackProvider,
    private val minSignalsToLearn: Int = DEFAULT_MIN_SIGNALS,
) {
    @Suppress("ReturnCount")
    suspend fun learn(): Outcome<StyleFingerprint, DomainError> {
        val signals = feedbackProvider.findSinceLastLearn()
        if (signals.size < minSignalsToLearn) {
            return Outcome.Err(
                DomainError.InsufficientProfile(
                    collectedSamples = signals.size,
                    required = minSignalsToLearn,
                ),
            )
        }
        val baseline =
            fingerprintStore.findLatest()
                ?: return Outcome.Err(
                    DomainError.InsufficientProfile(
                        collectedSamples = 0,
                        required = 1,
                    ),
                )
        val baselineFp =
            runCatching { FingerprintJson.fromJson(baseline.fingerprintJson) }
                .getOrElse { e ->
                    return Outcome.Err(
                        DomainError.LlmFailure(LlmFailureReason.INVALID_RESPONSE, cause = e),
                    )
                }

        val prompt = buildPrompt(baselineFp, signals)
        val llmResult =
            llmProvider.generateCandidates(
                prompt = prompt,
                maxTokens = LEARN_MAX_TOKENS,
                n = 1,
            )
        val candidateText =
            when (llmResult) {
                is Outcome.Ok ->
                    llmResult.value.firstOrNull()?.text
                        ?: return Outcome.Err(DomainError.LlmFailure(LlmFailureReason.INVALID_RESPONSE))

                is Outcome.Err -> return llmResult
            }

        val merged =
            runCatching { parseMerged(candidateText, baselineFp) }
                .getOrElse { e ->
                    return Outcome.Err(
                        DomainError.LlmFailure(LlmFailureReason.INVALID_RESPONSE, cause = e),
                    )
                }
        val newVersion = fingerprintStore.nextVersion()
        val withVersion = merged.copy(version = newVersion, createdAt = Instant.now())
        fingerprintStore.insert(
            StyleFingerprintEntity(
                version = newVersion,
                createdAtEpochMs = withVersion.createdAt.toEpochMilli(),
                sampleSize = withVersion.sampleSize,
                partnerScopeId = withVersion.partnerScope?.value,
                fingerprintJson = FingerprintJson.toJson(withVersion),
            ),
        )
        return Outcome.Ok(withVersion)
    }

    /**
     * Visible for tests. The privacy red line lives here: the only
     * user-content field that reaches the prompt is
     * [FeedbackSignal.Modify.editedContent] — i.e. user's own writing.
     */
    internal fun buildPrompt(
        baseline: StyleFingerprint,
        signals: List<FeedbackSignal>,
    ): String {
        val adopts = signals.count { it is FeedbackSignal.Adopt }
        val discards = signals.filterIsInstance<FeedbackSignal.Discard>()
        val modifies = signals.filterIsInstance<FeedbackSignal.Modify>()
        val baselineJson = FingerprintJson.toJson(baseline)
        return buildString {
            appendLine("你是一个风格画像增量学习助手。")
            appendLine("基线画像 JSON：")
            appendLine(baselineJson)
            appendLine()
            appendLine("用户在该基线下的反馈统计：")
            appendLine("- 采纳：$adopts 条")
            appendLine("- 丢弃：${discards.size} 条；按原因：${discards.groupingBy { it.reason.name }.eachCount()}")
            appendLine("- 修改：${modifies.size} 条；用户实际改写后的文本如下，每行一条：")
            modifies.forEach { m -> appendLine("  - ${m.editedContent}") }
            appendLine()
            appendLine("请基于上述反馈，对画像做轻量调整（不要大改），仅以同样 JSON 结构输出，不加任何解释。")
        }
    }

    private fun parseMerged(
        text: String,
        baseline: StyleFingerprint,
    ): StyleFingerprint {
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        require(jsonStart >= 0 && jsonEnd > jsonStart) {
            "LLM response does not contain a JSON object"
        }
        val jsonStr = text.substring(jsonStart, jsonEnd + 1)
        val parsed = FingerprintJson.fromJson(jsonStr)
        return parsed.copy(
            sampleSize = baseline.sampleSize,
            partnerScope = baseline.partnerScope,
        )
    }

    companion object {
        const val DEFAULT_MIN_SIGNALS: Int = 20
        const val LEARN_MAX_TOKENS: Int = 2_000
    }
}

/**
 * Indirection so [IncrementalLearner] can be tested without depending on
 * the Room-backed [com.stylemirror.core.data.repository.FeedbackRepository]
 * directly. Production wiring passes a thin adapter that delegates to it.
 */
fun interface FeedbackProvider {
    suspend fun findSinceLastLearn(): List<FeedbackSignal>
}
