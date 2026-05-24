package com.stylemirror.feature.imports.profiling

import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.profiling.FingerprintJson
import com.stylemirror.core.data.repository.CorpusSampleStore
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.feature.imports.sampling.ProfilingInput
import com.stylemirror.feature.imports.sampling.SampledMessage
import com.stylemirror.infra.llm.FakeLLMProvider
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.emptyFlow
import io.kotest.matchers.collections.shouldContain as shouldContainElement

private class StubFingerprintStore : StyleFingerprintStore {
    val inserted = mutableListOf<StyleFingerprintEntity>()

    override suspend fun insert(entity: StyleFingerprintEntity): Long {
        inserted += entity
        return inserted.size.toLong()
    }

    override suspend fun findLatest(): StyleFingerprintEntity? = inserted.lastOrNull()

    override suspend fun findLatestForScope(partnerScopeId: String?): StyleFingerprintEntity? =
        inserted.lastOrNull { it.partnerScopeId == partnerScopeId }

    override fun observeHistory(partnerScopeId: String?) = emptyFlow<List<StyleFingerprintEntity>>()

    override suspend fun findByVersion(version: Int): StyleFingerprintEntity? = inserted.find { it.version == version }

    override suspend fun nextVersion(): Int = inserted.size + 1
}

private class StubCorpusStore : CorpusSampleStore {
    val inserted = mutableListOf<CorpusSampleEntity>()

    override suspend fun insertAll(samples: List<CorpusSampleEntity>): List<Long> {
        inserted += samples
        return List(samples.size) { (inserted.size - samples.size + it).toLong() }
    }

    override suspend fun findActiveByVersion(version: Int) =
        inserted.filter { it.fingerprintVersion == version && it.deletedAtEpochMs == null }

    override suspend fun findAllByVersion(version: Int) = inserted.filter { it.fingerprintVersion == version }

    override fun observeActiveByVersion(version: Int) = emptyFlow<List<CorpusSampleEntity>>()

    override suspend fun softDelete(
        rowId: Long,
        nowEpochMs: Long,
    ): Int = 0

    override suspend fun undelete(rowId: Long): Int = 0
}

private val VALID_WRAPPER_JSON =
    """
    {
      "fingerprint": {
        "linguistic": { "formality": "CASUAL", "vocabularyComplexity": 0.3, "sentencePattern": "SHORT_FRAGMENTED", "signaturePhrases": ["哈哈"] },
        "emotional":  { "emojiDensity": 2.0, "exclamationFrequency": 0.4, "tone": "BALANCED", "preferredEmojis": ["😄"] },
        "humor":      { "frequency": 0.5, "types": ["OBSERVATIONAL"] },
        "avoidance":  { "topicsAvoided": [], "hedgingFrequency": 0.2, "deflectionStrategy": "REDIRECT" },
        "pacing":     { "avgMessageLength": 25.0, "avgMessagesPerTurn": 1.5, "responseDelay": "MINUTES" },
        "sensitive":  { "directness": "INDIRECT", "approach": "EMPATHETIC" }
      },
      "behavior_rules_md": "## 我的说话风格\n- 高频口头禅：「确实」「绷不住了」\n- 拒绝时倾向先说「行吧」+ 转移话题",
      "corpus_samples": [
        {"text": "在的，怎么了", "scenario": "日常问候"},
        {"text": "确实，挺有道理", "scenario": "表态"},
        {"text": "行吧，下次再约", "scenario": "拒绝"},
        {"text": "我先睡了", "scenario": "冷处理"},
        {"text": "没事啦，下次注意就好", "scenario": "安慰"}
      ]
    }
    """.trimIndent()

private fun sampledMsg(content: String) = SampledMessage(content = content, timestampHint = null, sourceIndex = 0)

private fun standardInput() =
    ProfilingInput(
        myMessages =
            listOf(
                "在的，怎么了",
                "确实，挺有道理",
                "行吧，下次再约",
                "我先睡了",
                "没事啦，下次注意就好",
                "msg6",
                "msg7",
                "msg8",
                "msg9",
                "msg10",
                "msg11",
                "msg12",
                "msg13",
                "msg14",
                "msg15",
            ).map { sampledMsg(it) },
        totalSampled = 15,
        totalAvailable = 15,
    )

class PersonaProfilerTest : StringSpec({

    "profile returns Ok with fingerprint + behaviorRules + corpusSampleCount" {
        val repo = StubFingerprintStore()
        val corpus = StubCorpusStore()
        val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_WRAPPER_JSON))) }
        val profiler = PersonaProfiler(llm, repo, corpus)

        val result = profiler.profile(standardInput())
        result.shouldBeInstanceOf<Outcome.Ok<ProfileResult>>()
        val ok = (result as Outcome.Ok).value
        ok.fingerprint.linguistic.shouldNotBeNull()
        ok.behaviorRules.shouldContain("高频口头禅")
        ok.corpusSampleCount shouldBe 5
    }

    "profile persists fingerprint with behaviorRules + corpus rows" {
        val repo = StubFingerprintStore()
        val corpus = StubCorpusStore()
        val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_WRAPPER_JSON))) }
        val profiler = PersonaProfiler(llm, repo, corpus)

        profiler.profile(standardInput())
        repo.inserted shouldHaveSize 1
        repo.inserted.single().behaviorRules.shouldContain("高频口头禅")
        corpus.inserted shouldHaveSize 5
        corpus.inserted.first().fingerprintVersion shouldBe 1
        corpus.inserted.map { it.scenario }.toSet() shouldBe
            setOf("日常问候", "表态", "拒绝", "冷处理", "安慰")
    }

    "fabricated corpus samples are dropped (text not in input)" {
        val repo = StubFingerprintStore()
        val corpus = StubCorpusStore()
        val withFabrication =
            VALID_WRAPPER_JSON.replace(
                "{\"text\": \"在的，怎么了\", \"scenario\": \"日常问候\"}",
                "{\"text\": \"我从来没说过这句话\", \"scenario\": \"日常问候\"}",
            )
        val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = withFabrication))) }
        val profiler = PersonaProfiler(llm, repo, corpus)

        profiler.profile(standardInput())
        // 5 samples in JSON, 1 fabricated → 4 persisted
        corpus.inserted shouldHaveSize 4
        corpus.inserted.map { it.text }.shouldContainElement("确实，挺有道理")
        corpus.inserted.none { it.text == "我从来没说过这句话" } shouldBe true
    }

    "duplicate corpus texts are de-duplicated" {
        val repo = StubFingerprintStore()
        val corpus = StubCorpusStore()
        val withDup =
            """
            {
              "fingerprint": {
                "linguistic": { "formality": "CASUAL", "vocabularyComplexity": 0.3, "sentencePattern": "MIXED", "signaturePhrases": [] },
                "emotional":  { "emojiDensity": 1.0, "exclamationFrequency": 0.2, "tone": "BALANCED", "preferredEmojis": [] },
                "humor":      { "frequency": 0.3, "types": ["NONE"] },
                "avoidance":  { "topicsAvoided": [], "hedgingFrequency": 0.2, "deflectionStrategy": "NONE" },
                "pacing":     { "avgMessageLength": 20.0, "avgMessagesPerTurn": 1.0, "responseDelay": "MINUTES" },
                "sensitive":  { "directness": "INDIRECT", "approach": "EMPATHETIC" }
              },
              "behavior_rules_md": "x",
              "corpus_samples": [
                {"text": "在的，怎么了", "scenario": "日常问候"},
                {"text": "在的，怎么了", "scenario": "日常问候"},
                {"text": "我先睡了", "scenario": "冷处理"}
              ]
            }
            """.trimIndent()
        val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = withDup))) }
        val profiler = PersonaProfiler(llm, repo, corpus)

        profiler.profile(standardInput())
        corpus.inserted shouldHaveSize 2
    }

    "profile returns InsufficientProfile when no messages" {
        val repo = StubFingerprintStore()
        val corpus = StubCorpusStore()
        val llm = FakeLLMProvider()
        val profiler = PersonaProfiler(llm, repo, corpus)

        val input = ProfilingInput(myMessages = emptyList(), totalSampled = 0, totalAvailable = 0)
        val result = profiler.profile(input)
        result.shouldBeInstanceOf<Outcome.Err<*>>()
        (result as Outcome.Err).error.shouldBeInstanceOf<DomainError.InsufficientProfile>()
    }

    "profile propagates LLM errors" {
        val repo = StubFingerprintStore()
        val corpus = StubCorpusStore()
        val llmErr = DomainError.LlmFailure(com.stylemirror.domain.error.LlmFailureReason.TIMEOUT)
        val llm = FakeLLMProvider { _, _ -> Outcome.Err(llmErr) }
        val profiler = PersonaProfiler(llm, repo, corpus)

        val result = profiler.profile(standardInput())
        result shouldBe Outcome.Err(llmErr)
    }

    "buildPrompt with priorBehaviorRules embeds them in prompt for evolution" {
        val repo = StubFingerprintStore()
        val corpus = StubCorpusStore()
        val llm = FakeLLMProvider()
        val profiler = PersonaProfiler(llm, repo, corpus)

        val prompt =
            profiler.buildPrompt(
                standardInput(),
                priorBehaviorRules = "## 旧规则\n- 我之前喜欢用「嘛」",
            )
        prompt.shouldContain("旧规则")
        prompt.shouldContain("嘛")
    }

    "FingerprintJson round-trips" {
        val innerOnly =
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
        val fp = FingerprintJson.fromJson(innerOnly)
        val json2 = FingerprintJson.toJson(fp)
        val fp2 = FingerprintJson.fromJson(json2)
        fp2.linguistic.formality shouldBe fp.linguistic.formality
        fp2.emotional.tone shouldBe fp.emotional.tone
    }
})
