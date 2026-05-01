package com.stylemirror.infra.llm

import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.LlmFailureReason
import com.stylemirror.domain.error.Outcome
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeLLMProviderTest {
    @Test
    fun `default responder returns n candidates with non-blank text`() =
        runTest {
            val provider = FakeLLMProvider()

            val outcome = provider.generateCandidates(prompt = "hello", n = 3)

            val candidates = (outcome as Outcome.Ok).value
            candidates shouldHaveSize 3
            candidates.forEach { it.text.shouldNotBeBlank() }
        }

    @Test
    fun `default responder honours requested n`() =
        runTest {
            val provider = FakeLLMProvider()

            val outcome = provider.generateCandidates(prompt = "hello", n = 5)

            (outcome as Outcome.Ok).value shouldHaveSize 5
        }

    @Test
    fun `custom responder receives prompt and n`() =
        runTest {
            var seenPrompt: String? = null
            var seenN: Int? = null
            val provider =
                FakeLLMProvider { p, n ->
                    seenPrompt = p
                    seenN = n
                    Outcome.Ok(emptyList())
                }

            provider.generateCandidates(prompt = "echo me", n = 7)

            seenPrompt shouldBe "echo me"
            seenN shouldBe 7
        }

    @Test
    fun `custom responder can stage an error`() =
        runTest {
            val provider =
                FakeLLMProvider { _, _ ->
                    Outcome.Err(DomainError.LlmFailure(reason = LlmFailureReason.RATE_LIMITED))
                }

            val outcome = provider.generateCandidates(prompt = "hi")

            val err = (outcome as Outcome.Err).error
            (err as DomainError.LlmFailure).reason shouldBe LlmFailureReason.RATE_LIMITED
        }

    @Test
    fun `custom responder can stage NotImplemented for stub providers`() =
        runTest {
            val provider = FakeLLMProvider { _, _ -> Outcome.Err(DomainError.NotImplemented) }

            val outcome = provider.generateCandidates(prompt = "hi")

            (outcome as Outcome.Err).error shouldBe DomainError.NotImplemented
        }
}
