package com.stylemirror.app.onboarding

import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.repository.CorpusSampleStore
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.LlmFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.security.SecureKeyStore
import com.stylemirror.feature.imports.profiling.PersonaProfiler
import com.stylemirror.infra.llm.FakeLLMProvider
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private val VALID_PROFILE_JSON =
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
      "behavior_rules_md": "## 风格\n- 简短直接",
      "corpus_samples": []
    }
    """.trimIndent()

private class StubStore : StyleFingerprintStore {
    val inserted = mutableListOf<StyleFingerprintEntity>()

    override suspend fun insert(entity: StyleFingerprintEntity): Long {
        inserted += entity
        return inserted.size.toLong()
    }

    override suspend fun findLatest(): StyleFingerprintEntity? = inserted.lastOrNull()

    override suspend fun findLatestForScope(partnerScopeId: String?): StyleFingerprintEntity? =
        inserted.lastOrNull { it.partnerScopeId == partnerScopeId }

    override fun observeHistory(partnerScopeId: String?) = emptyFlow<List<StyleFingerprintEntity>>()

    override suspend fun findByVersion(version: Int) = inserted.firstOrNull { it.version == version }

    override suspend fun nextVersion(): Int = inserted.size + 1
}

private class StubCorpusStore : CorpusSampleStore {
    val inserted = mutableListOf<CorpusSampleEntity>()

    override suspend fun insertAll(samples: List<CorpusSampleEntity>): List<Long> {
        inserted += samples
        return List(samples.size) { it.toLong() }
    }

    override suspend fun findActiveByVersion(version: Int) =
        inserted.filter { it.fingerprintVersion == version && it.deletedAtEpochMs == null }

    override suspend fun findAllByVersion(version: Int) = inserted.filter { it.fingerprintVersion == version }

    override fun observeActiveByVersion(version: Int) = emptyFlow<List<CorpusSampleEntity>>()

    override suspend fun softDelete(
        rowId: Long,
        nowEpochMs: Long,
    ) = 0

    override suspend fun undelete(rowId: Long) = 0
}

private fun corpusOf(messageCount: Int): String =
    buildString {
        for (i in 1..messageCount) {
            appendLine("我：消息编号$i")
            appendLine("张三：回复编号$i")
        }
    }

private fun newViewModel(
    profiler: PersonaProfiler,
    keyStore: SecureKeyStore = stubKeyStore(apiKey = "fake-key"),
): OnboardingViewModel = OnboardingViewModel(personaProfiler = profiler, keyStore = keyStore)

private fun stubKeyStore(apiKey: String?): SecureKeyStore =
    object : SecureKeyStore {
        override suspend fun put(
            name: String,
            value: String,
        ) = Unit

        override suspend fun get(name: String): String? = apiKey

        override suspend fun remove(name: String) = Unit

        override suspend fun clearAll() = Unit
    }

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest : StringSpec({

    val testDispatcher = StandardTestDispatcher()

    beforeSpec { Dispatchers.setMain(testDispatcher) }
    afterSpec { Dispatchers.resetMain() }

    "parseAliases splits on common separators and trims" {
        OnboardingViewModel.parseAliases("我, 小张, 阿张") shouldBe setOf("我", "小张", "阿张")
        OnboardingViewModel.parseAliases("我，小张；阿张、张三") shouldBe setOf("我", "小张", "阿张", "张三")
        OnboardingViewModel.parseAliases("  ") shouldBe emptySet()
    }

    "domainErrorMessage maps KEY_MISSING to a setting hint" {
        val msg =
            OnboardingViewModel.domainErrorMessage(
                error = DomainError.LlmFailure(LlmFailureReason.KEY_MISSING),
                sampleCount = 30,
            )
        msg shouldContain "API Key"
    }

    "domainErrorMessage surfaces InsufficientProfile sample shortfall" {
        val msg =
            OnboardingViewModel.domainErrorMessage(
                error = DomainError.InsufficientProfile(collectedSamples = 5, required = 10),
                sampleCount = 5,
            )
        msg shouldContain "5"
        msg shouldContain "10"
    }

    "runProfiling produces Ready state with summary on happy path" {
        runTest(testDispatcher) {
            val store = StubStore()
            val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_PROFILE_JSON))) }
            val vm = newViewModel(PersonaProfiler(llm, store, StubCorpusStore()))

            vm.onAliasesChange("我")
            vm.onPasteChange(corpusOf(15))
            vm.runProfiling()
            testScheduler.advanceUntilIdle()

            val state = vm.state.value
            state.shouldBeInstanceOf<OnboardingState.Ready>()
            state.summary.sampleCount shouldBe 15
            state.summary.linguistic shouldContain "随意"
            store.inserted.size shouldBe 1
        }
    }

    "runProfiling reports InsufficientProfile when sample count is below threshold" {
        runTest(testDispatcher) {
            val store = StubStore()
            val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_PROFILE_JSON))) }
            val vm = newViewModel(PersonaProfiler(llm, store, StubCorpusStore()))

            vm.onAliasesChange("我")
            vm.onPasteChange(corpusOf(3)) // only 3 Me messages, below MIN_SAMPLES_REQUIRED=10
            vm.runProfiling()
            testScheduler.advanceUntilIdle()

            val state = vm.state.value
            state.shouldBeInstanceOf<OnboardingState.Error>()
            state.message shouldContain "至少需要"
            store.inserted.size shouldBe 0
        }
    }

    "runProfiling surfaces LLM AUTH error to user-friendly message" {
        runTest(testDispatcher) {
            val store = StubStore()
            val llm =
                FakeLLMProvider { _, _ ->
                    Outcome.Err(DomainError.LlmFailure(LlmFailureReason.AUTH))
                }
            val vm = newViewModel(PersonaProfiler(llm, store, StubCorpusStore()))

            vm.onAliasesChange("我")
            vm.onPasteChange(corpusOf(15))
            vm.runProfiling()
            testScheduler.advanceUntilIdle()

            val state = vm.state.value
            state.shouldBeInstanceOf<OnboardingState.Error>()
            state.message shouldContain "API Key"
            store.inserted.size shouldBe 0
        }
    }

    "confirmAliases ignores empty alias input and stays on AskAliases" {
        val vm =
            newViewModel(
                PersonaProfiler(
                    FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_PROFILE_JSON))) },
                    StubStore(),
                    StubCorpusStore(),
                ),
            )
        vm.onAliasesChange("   ")
        vm.confirmAliases()
        vm.state.value shouldBe OnboardingState.AskAliases
    }

    "loadFromTextSource 把 source 返回的文本写入 pasteText" {
        runTest(testDispatcher) {
            val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_PROFILE_JSON))) }
            val vm = newViewModel(PersonaProfiler(llm, StubStore(), StubCorpusStore()))

            vm.loadFromTextSource(TextSource { "我：从文件来\n张三：你好" })
            testScheduler.advanceUntilIdle()

            vm.pasteText.value shouldContain "从文件来"
        }
    }

    "loadFromTextSource source 抛错时进入 Error 态，包含原因" {
        runTest(testDispatcher) {
            val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_PROFILE_JSON))) }
            val vm = newViewModel(PersonaProfiler(llm, StubStore(), StubCorpusStore()))

            vm.loadFromTextSource(TextSource { error("文件过大（10 MB）") })
            testScheduler.advanceUntilIdle()

            val state = vm.state.value
            state.shouldBeInstanceOf<OnboardingState.Error>()
            state.message shouldContain "文件导入失败"
            state.message shouldContain "文件过大"
        }
    }

    "runProfiling 在没有 API Key 时立刻 Error，不调用 LLM" {
        runTest(testDispatcher) {
            val store = StubStore()
            var llmCalls = 0
            val llm =
                FakeLLMProvider { _, _ ->
                    llmCalls++
                    Outcome.Ok(listOf(Candidate(text = VALID_PROFILE_JSON)))
                }
            val vm =
                newViewModel(PersonaProfiler(llm, store, StubCorpusStore()), keyStore = stubKeyStore(apiKey = null))

            vm.onAliasesChange("我")
            vm.onPasteChange(corpusOf(15))
            vm.runProfiling()
            testScheduler.advanceUntilIdle()

            val state = vm.state.value
            state.shouldBeInstanceOf<OnboardingState.Error>()
            state.message shouldContain "API Key"
            llmCalls shouldBe 0
            store.inserted.size shouldBe 0
        }
    }

    "runProfiling 在 API Key 为空白时也立刻 Error" {
        runTest(testDispatcher) {
            val store = StubStore()
            val llm = FakeLLMProvider { _, _ -> Outcome.Ok(listOf(Candidate(text = VALID_PROFILE_JSON))) }
            val vm =
                newViewModel(PersonaProfiler(llm, store, StubCorpusStore()), keyStore = stubKeyStore(apiKey = "   "))

            vm.onAliasesChange("我")
            vm.onPasteChange(corpusOf(15))
            vm.runProfiling()
            testScheduler.advanceUntilIdle()

            val state = vm.state.value
            state.shouldBeInstanceOf<OnboardingState.Error>()
            state.message shouldContain "API Key"
        }
    }
})
