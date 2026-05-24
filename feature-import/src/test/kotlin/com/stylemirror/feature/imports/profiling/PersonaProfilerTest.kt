package com.stylemirror.feature.imports.profiling

import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.profiling.FingerprintJson
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.feature.imports.sampling.ProfilingInput
import com.stylemirror.feature.imports.sampling.SampledMessage
import com.stylemirror.infra.llm.FakeLLMProvider
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.emptyFlow

/**
 * Stub [StyleFingerprintStore] for unit tests.
 */
private class StubFingerprintStore : StyleFingerprintStore {
    val inserted = mutableListOf<StyleFingerprintEntity>()

    override suspend fun insert(entity: StyleFingerprintEntity): Long {
        inserted += entity
        return inserted.size.toLong()
    }

    override suspend fun findLatest(): StyleFingerprintEntity? = inserted.lastOrNull()

    override suspend fun findLatestForScope(partnerScopeId: String?): StyleFingerprintEntity? =
        inserted.filter { it.partnerScopeId == partnerScopeId }.lastOrNull()

    override fun observeHistory(partnerScopeId: String?) =
        kotlinx.coroutines.flow.emptyFlow<List<StyleFingerprintEntity>>()

    override suspend fun findByVersion(version: Int): StyleFingerprintEntity? = inserted.find { it.version == version }

    override suspend fun nextVersion(): Int = inserted.size + 1
}

private val VALID_JSON =
    """
    {
      "linguistic": { "formality": "CASUAL", "vocabularyComplexity": 0.3, "sentencePattern": "SHORT_FRAGMENTED", "signaturePhrases": ["哈哈"] },
      "emotional":  { "emojiDensity": 2.0, "exclamationFrequency": 0.4, "tone": "BALANCED", "preferredEmojis": ["😄"] },
      "humor":      { "frequency": 0.5, "types": ["OBSERVATIONAL"] },
      "avoidance":  { "topicsAvoided": [], "hedgingFrequency": 0.2, "deflectionStrategy": "REDIRECT" },
      "pacing":     { "avgMessageLength": 25.0, "avgMessagesPerTurn": 1.5, "responseDelay": "MINUTES" },
      "sensitive":  { "directness": "INDIRECT", "approach": "EMPATHETIC" }
    }
    """.trimIndent()

private fun sampledMsg(content: String) = SampledMessage(content = content, timestampHint = null, sourceIndex = 0)

class PersonaProfilerTest : StringSpec({

    // ---- happy path ---------------------------------------------------------

    "profile returns Ok with all 6 dimensions non-null" {
        val repo = StubFingerprintStore()
        val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_JSON))) }
        val profiler = PersonaProfiler(llm, repo)

        val input =
            ProfilingInput(
                myMessages = (1..15).map { sampledMsg("msg$it") },
                totalSampled = 15,
                totalAvailable = 15,
            )
        val result = profiler.profile(input)
        result.shouldBeInstanceOf<Outcome.Ok<*>>()
        val fp = (result as Outcome.Ok).value
        fp.linguistic.shouldNotBeNull()
        fp.emotional.shouldNotBeNull()
        fp.humor.shouldNotBeNull()
        fp.avoidance.shouldNotBeNull()
        fp.pacing.shouldNotBeNull()
        fp.sensitive.shouldNotBeNull()
    }

    "profile persists fingerprint to repository" {
        val repo = StubFingerprintStore()
        val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_JSON))) }
        val profiler = PersonaProfiler(llm, repo)

        val input =
            ProfilingInput(
                myMessages = (1..15).map { sampledMsg("msg$it") },
                totalSampled = 15,
                totalAvailable = 15,
            )
        profiler.profile(input)
        repo.inserted.size shouldBe 1
    }

    // ---- prompt privacy guard -----------------------------------------------

    "buildPrompt does not require Theirs content — ProfilingInput only has Me" {
        val repo = StubFingerprintStore()
        val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_JSON))) }
        val profiler = PersonaProfiler(llm, repo)

        val myTexts = listOf("我说了这个", "然后说了那个", "最后说再见")
        val input =
            ProfilingInput(
                myMessages = myTexts.map { SampledMessage(it, null, 0) },
                totalSampled = 3,
                totalAvailable = 3,
            )
        val prompt = profiler.buildPrompt(input)
        // The prompt should contain the user's own messages
        myTexts.forEach { text ->
            (prompt.contains(text)) shouldBe true
        }
        // ProfilingInput has no Theirs field — can't accidentally leak it
        // This test documents the type guarantee rather than asserting a string absence
        (prompt.contains("对方")) shouldBe false
    }

    // ---- InsufficientProfile error ------------------------------------------

    "profile returns InsufficientProfile when no messages" {
        val repo = StubFingerprintStore()
        val llm = FakeLLMProvider()
        val profiler = PersonaProfiler(llm, repo)

        val input =
            ProfilingInput(myMessages = emptyList(), totalSampled = 0, totalAvailable = 0)
        val result = profiler.profile(input)
        result.shouldBeInstanceOf<Outcome.Err<*>>()
        (result as Outcome.Err).error.shouldBeInstanceOf<DomainError.InsufficientProfile>()
    }

    // ---- LLM error propagation ----------------------------------------------

    "profile propagates LLM errors" {
        val repo = StubFingerprintStore()
        val llmErr = DomainError.LlmFailure(com.stylemirror.domain.error.LlmFailureReason.TIMEOUT)
        val llm = FakeLLMProvider { _, _ -> Outcome.Err(llmErr) }
        val profiler = PersonaProfiler(llm, repo)

        val input =
            ProfilingInput(
                myMessages = (1..15).map { sampledMsg("msg$it") },
                totalSampled = 15,
                totalAvailable = 15,
            )
        val result = profiler.profile(input)
        result shouldBe Outcome.Err(llmErr)
    }

    // ---- FingerprintJson round-trip -----------------------------------------

    "FingerprintJson round-trips through toJson/fromJson" {
        val fp = FingerprintJson.fromJson(VALID_JSON)
        val json2 = FingerprintJson.toJson(fp)
        val fp2 = FingerprintJson.fromJson(json2)
        // Key fields survive the round-trip
        fp2.linguistic.formality shouldBe fp.linguistic.formality
        fp2.emotional.tone shouldBe fp.emotional.tone
        fp2.humor.types shouldBe fp.humor.types
        fp2.avoidance.deflectionStrategy shouldBe fp.avoidance.deflectionStrategy
        fp2.pacing.responseDelay shouldBe fp.pacing.responseDelay
        fp2.sensitive.directness shouldBe fp.sensitive.directness
    }

    "FingerprintJson fromJson handles missing fields with defaults" {
        val minimalJson = "{}"
        val fp = FingerprintJson.fromJson(minimalJson)
        fp.linguistic.shouldNotBeNull()
        fp.emotional.shouldNotBeNull()
        fp.humor.types.isNotEmpty() shouldBe true
    }
})
