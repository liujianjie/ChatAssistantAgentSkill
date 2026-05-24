package com.stylemirror.feature.realtime.candidate

import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.conversation.MessageId
import com.stylemirror.domain.conversation.PartnerId
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.ImportFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.feature.realtime.matching.FakeStyleEngine
import com.stylemirror.infra.llm.FakeLLMProvider
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

private val PARTNER = PartnerId("p-test")
private val T0: Instant = Instant.parse("2026-05-01T00:00:00Z")

class CandidateGeneratorTest : StringSpec({

    // ---------- happy path ----------

    "generate returns 3 candidates by default" {
        val generator =
            CandidateGenerator(
                llmProvider = FakeLLMProvider(),
                styleEngine = FakeStyleEngine(),
            )
        val ctx =
            buildContext(
                myLines = listOf("好的"),
                theirLines = listOf("你好", "最近怎么样"),
            )
        val result = generator.generate(ctx)
        result.shouldBeInstanceOf<Outcome.Ok<List<Candidate>>>()
        result.value shouldHaveSize 3
    }

    "every candidate has a styleMatchScore between 0 and 1" {
        val generator =
            CandidateGenerator(
                llmProvider = FakeLLMProvider(),
                styleEngine = FakeStyleEngine(),
            )
        val ctx = buildContext(theirLines = listOf("最近怎么样"))
        val candidates = (generator.generate(ctx) as Outcome.Ok).value
        candidates.forEach { candidate ->
            val score = candidate.styleMatchScore.shouldNotBeNull()
            withClue("score must be in [0,1], was $score") {
                score.shouldBeBetween(0.0f, 1.0f, 0.0f)
            }
        }
    }

    // ---------- privacy guard ----------

    "prompt does not contain phone numbers from theirs messages" {
        val capturedPrompts = mutableListOf<String>()
        val generator =
            CandidateGenerator(
                llmProvider =
                    FakeLLMProvider { prompt, n ->
                        capturedPrompts += prompt
                        Outcome.Ok(List(n) { Candidate("ok") })
                    },
                styleEngine = FakeStyleEngine(),
            )
        val ctx = buildContext(theirLines = listOf("我的手机是13812345678，有事打电话"))
        generator.generate(ctx)

        val prompt = capturedPrompts.single()
        withClue("prompt should not contain the raw phone number") {
            (prompt.contains("13812345678")) shouldBe false
        }
    }

    "prompt does not contain national ID numbers from theirs messages" {
        val capturedPrompts = mutableListOf<String>()
        val generator =
            CandidateGenerator(
                llmProvider =
                    FakeLLMProvider { prompt, n ->
                        capturedPrompts += prompt
                        Outcome.Ok(List(n) { Candidate("ok") })
                    },
                styleEngine = FakeStyleEngine(),
            )
        val ctx = buildContext(theirLines = listOf("身份证号是110101199001011234，请核对"))
        generator.generate(ctx)

        val prompt = capturedPrompts.single()
        withClue("prompt should not contain the national ID") {
            (prompt.contains("110101199001011234")) shouldBe false
        }
    }

    "prompt does not contain bank card numbers from theirs messages" {
        val capturedPrompts = mutableListOf<String>()
        val generator =
            CandidateGenerator(
                llmProvider =
                    FakeLLMProvider { prompt, n ->
                        capturedPrompts += prompt
                        Outcome.Ok(List(n) { Candidate("ok") })
                    },
                styleEngine = FakeStyleEngine(),
            )
        val ctx = buildContext(theirLines = listOf("卡号6222200012345678转账给我"))
        generator.generate(ctx)

        val prompt = capturedPrompts.single()
        withClue("prompt should not contain the bank card number") {
            (prompt.contains("6222200012345678")) shouldBe false
        }
    }

    // ---------- their-message cap ----------

    "prompt contains at most maxTheirMessages lines from the other side" {
        val capturedPrompts = mutableListOf<String>()
        val maxAllowed = 5
        val generator =
            CandidateGenerator(
                llmProvider =
                    FakeLLMProvider { prompt, n ->
                        capturedPrompts += prompt
                        Outcome.Ok(List(n) { Candidate("ok") })
                    },
                styleEngine = FakeStyleEngine(),
                maxTheirMessages = maxAllowed,
            )
        // Provide 15 "theirs" messages — only the last maxAllowed should appear.
        val theirLines = (1..15).map { "对方消息$it" }
        val ctx = buildContext(theirLines = theirLines)
        generator.generate(ctx)

        val snippet = capturedPrompts.single()
        // The prompt section "【对方最近消息（已脱敏）】" should have ≤ maxAllowed lines
        // that start with "对方消息". Messages 1–10 must not appear.
        withClue("messages 1–10 should be cut off") {
            (1..10).forEach { i ->
                (snippet.contains("对方消息$i\n") || snippet.endsWith("对方消息$i")) shouldBe false
            }
        }
        withClue("last $maxAllowed messages should be present") {
            (11..15).forEach { i ->
                snippet.contains("对方消息$i") shouldBe true
            }
        }
    }

    // ---------- empty context ----------

    "generate returns ImportFailure.EMPTY_INPUT for an empty context" {
        val generator =
            CandidateGenerator(
                llmProvider = FakeLLMProvider(),
                styleEngine = FakeStyleEngine(),
            )
        val ctx = ConversationContext(partnerId = PARTNER, messages = emptyList())
        val result = generator.generate(ctx)
        result.shouldBeInstanceOf<Outcome.Err<DomainError>>()
        val err = (result as Outcome.Err).error
        err.shouldBeInstanceOf<DomainError.ImportFailure>()
        err.reason shouldBe ImportFailureReason.EMPTY_INPUT
    }

    // ---------- LLM error propagation ----------

    "generate propagates LLM errors without wrapping" {
        val llmError = DomainError.LlmFailure(com.stylemirror.domain.error.LlmFailureReason.TIMEOUT)
        val generator =
            CandidateGenerator(
                llmProvider = FakeLLMProvider { _, _ -> Outcome.Err(llmError) },
                styleEngine = FakeStyleEngine(),
            )
        val ctx = buildContext(theirLines = listOf("你好"))
        val result = generator.generate(ctx)
        result shouldBe Outcome.Err(llmError)
    }
})

// ---------- helpers ----------

private fun buildContext(
    myLines: List<String> = listOf("好的"),
    theirLines: List<String> = emptyList(),
): ConversationContext {
    val messages = mutableListOf<Message>()
    var idx = 0
    theirLines.forEach { line ->
        messages +=
            Message.Theirs(
                id = MessageId("t-$idx"),
                content = line,
                sentAt = T0.plusSeconds(idx.toLong()),
                displayName = "对方",
            )
        idx++
    }
    myLines.forEach { line ->
        messages +=
            Message.Mine(
                id = MessageId("m-$idx"),
                content = line,
                sentAt = T0.plusSeconds(idx.toLong()),
            )
        idx++
    }
    return ConversationContext(partnerId = PARTNER, messages = messages)
}
