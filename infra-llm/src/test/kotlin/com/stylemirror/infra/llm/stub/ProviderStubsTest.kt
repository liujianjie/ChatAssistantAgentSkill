package com.stylemirror.infra.llm.stub

import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.infra.llm.claude.ClaudeProvider
import com.stylemirror.infra.llm.qwen.QwenProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ProviderStubsTest {
    @Test
    fun `Claude stub returns NotImplemented`() =
        runTest {
            val outcome = ClaudeProvider().generateCandidates(prompt = "ping")
            (outcome as Outcome.Err).error shouldBe DomainError.NotImplemented
        }

    @Test
    fun `Qwen stub returns NotImplemented`() =
        runTest {
            val outcome = QwenProvider().generateCandidates(prompt = "ping")
            (outcome as Outcome.Err).error shouldBe DomainError.NotImplemented
        }
}
