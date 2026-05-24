package com.stylemirror.feature.realtime.feedback

import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.profiling.FingerprintJson
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.feedback.CandidateId
import com.stylemirror.domain.feedback.DiscardReason
import com.stylemirror.domain.feedback.FeedbackSignal
import com.stylemirror.infra.llm.FakeLLMProvider
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import java.time.Instant

private val BASELINE_JSON =
    """
    {
      "version": 1,
      "createdAt": "2026-05-01T00:00:00Z",
      "sampleSize": 100,
      "partnerScope": null,
      "linguistic": { "formality": "CASUAL", "vocabularyComplexity": 0.3, "sentencePattern": "SHORT_FRAGMENTED", "signaturePhrases": ["哈哈"] },
      "emotional":  { "emojiDensity": 2.0, "exclamationFrequency": 0.4, "tone": "BALANCED", "preferredEmojis": ["😄"] },
      "humor":      { "frequency": 0.5, "types": ["OBSERVATIONAL"] },
      "avoidance":  { "topicsAvoided": [], "hedgingFrequency": 0.2, "deflectionStrategy": "REDIRECT" },
      "pacing":     { "avgMessageLength": 25.0, "avgMessagesPerTurn": 1.5, "responseDelay": "MINUTES" },
      "sensitive":  { "directness": "INDIRECT", "approach": "EMPATHETIC" }
    }
    """.trimIndent()

private class StubStore : StyleFingerprintStore {
    val inserted = mutableListOf<StyleFingerprintEntity>()

    init {
        inserted +=
            StyleFingerprintEntity(
                version = 1,
                createdAtEpochMs = 1_700_000_000_000,
                sampleSize = 100,
                partnerScopeId = null,
                fingerprintJson = BASELINE_JSON,
            )
    }

    override suspend fun insert(entity: StyleFingerprintEntity): Long {
        inserted += entity
        return inserted.size.toLong()
    }

    override suspend fun findLatest() = inserted.lastOrNull()

    override suspend fun findLatestForScope(partnerScopeId: String?) =
        inserted.lastOrNull { it.partnerScopeId == partnerScopeId }

    override fun observeHistory(partnerScopeId: String?) = emptyFlow<List<StyleFingerprintEntity>>()

    override suspend fun findByVersion(version: Int) = inserted.firstOrNull { it.version == version }

    override suspend fun nextVersion(): Int = (inserted.maxOfOrNull { it.version } ?: 0) + 1
}

private fun buildSignals(
    adopts: Int = 0,
    discards: List<DiscardReason> = emptyList(),
    modifies: List<String> = emptyList(),
): List<FeedbackSignal> {
    val out = mutableListOf<FeedbackSignal>()
    repeat(adopts) { i ->
        out +=
            FeedbackSignal.Adopt(
                candidateId = CandidateId("a$i"),
                fingerprintVersion = 1,
                createdAt = Instant.now(),
            )
    }
    discards.forEachIndexed { i, reason ->
        out +=
            FeedbackSignal.Discard(
                candidateId = CandidateId("d$i"),
                fingerprintVersion = 1,
                createdAt = Instant.now(),
                reason = reason,
            )
    }
    modifies.forEachIndexed { i, text ->
        out +=
            FeedbackSignal.Modify(
                candidateId = CandidateId("m$i"),
                fingerprintVersion = 1,
                createdAt = Instant.now(),
                editedContent = text,
            )
    }
    return out
}

class IncrementalLearnerTest : StringSpec({

    "returns InsufficientProfile when fewer than minSignals are available" {
        runTest {
            val store = StubStore()
            val signals = buildSignals(adopts = 5)
            val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = BASELINE_JSON))) }
            val learner =
                IncrementalLearner(
                    llmProvider = llm,
                    fingerprintStore = store,
                    feedbackProvider = { signals },
                    minSignalsToLearn = 20,
                )
            val result = learner.learn()
            result.shouldBeInstanceOf<Outcome.Err<DomainError>>()
            (result as Outcome.Err).error
                .shouldBeInstanceOf<DomainError.InsufficientProfile>()
        }
    }

    "happy path bumps fingerprint version monotonically" {
        runTest {
            val store = StubStore()
            val signals = buildSignals(adopts = 12, modifies = listOf("加个表情~", "好好好"))
            val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = BASELINE_JSON))) }
            val learner =
                IncrementalLearner(
                    llmProvider = llm,
                    fingerprintStore = store,
                    feedbackProvider = { signals + signals.take(6) },
                    minSignalsToLearn = 10,
                )
            val result = learner.learn()
            result.shouldBeInstanceOf<Outcome.Ok<*>>()
            val fp = (result as Outcome.Ok).value
            fp.version shouldBe 2
            store.inserted.size shouldBe 2
            store.inserted.last().version shouldBe 2
        }
    }

    "buildPrompt includes user-modified text but no Theirs content" {
        val store = StubStore()
        val signals =
            buildSignals(
                adopts = 5,
                discards = listOf(DiscardReason.OFF_STYLE, DiscardReason.OFF_TOPIC),
                modifies = listOf("我加个表情~", "好的没问题"),
            )
        val learner =
            IncrementalLearner(
                llmProvider = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = BASELINE_JSON))) },
                fingerprintStore = store,
                feedbackProvider = { signals },
            )
        val baselineFp = FingerprintJson.fromJson(BASELINE_JSON)
        val prompt = learner.buildPrompt(baselineFp, signals)

        // Whitebox asserts: prompt MUST contain Modify content, MUST contain
        // counts, MUST NOT mention any "对方" / "theirs" tokens (since the
        // type system does not even let us pass them in here).
        prompt shouldContain "我加个表情~"
        prompt shouldContain "好的没问题"
        prompt shouldContain "采纳：5"
        prompt shouldContain "丢弃：2"
        prompt shouldContain "修改：2"
        prompt shouldNotContain "对方"
        prompt shouldNotContain "theirs"
    }

    "LLM AUTH error propagates without writing a new version" {
        runTest {
            val store = StubStore()
            val sizeBefore = store.inserted.size
            val signals = buildSignals(adopts = 25)
            val llm =
                FakeLLMProvider { _, _ ->
                    Outcome.Err(
                        DomainError.LlmFailure(com.stylemirror.domain.error.LlmFailureReason.AUTH),
                    )
                }
            val learner =
                IncrementalLearner(
                    llmProvider = llm,
                    fingerprintStore = store,
                    feedbackProvider = { signals },
                )
            val result = learner.learn()
            result.shouldBeInstanceOf<Outcome.Err<DomainError>>()
            store.inserted.size shouldBe sizeBefore
        }
    }
})
