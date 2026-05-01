package com.stylemirror.domain.conversation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant

class ConversationModelTest : StringSpec({

    "MessageId rejects blank" {
        shouldThrow<IllegalArgumentException> { MessageId("") }
        shouldThrow<IllegalArgumentException> { MessageId("   ") }
    }

    "MessageId accepts non-blank" {
        MessageId("m-1").value shouldBe "m-1"
    }

    "PartnerId rejects blank" {
        shouldThrow<IllegalArgumentException> { PartnerId("") }
    }

    "PartnerId accepts non-blank" {
        PartnerId("partner-7").value shouldBe "partner-7"
    }

    "ConversationContext.myMessages returns only Mine" {
        val mine1 = mineMsg("m1", "hi")
        val theirs1 = theirsMsg("t1", "hello", "Alex")
        val mine2 = mineMsg("m2", "yo")
        val ctx =
            ConversationContext(
                partnerId = PartnerId("p"),
                messages = listOf(theirs1, mine1, mine2),
            )

        ctx.myMessages shouldContainExactly listOf(mine1, mine2)
        ctx.theirMessages shouldContainExactly listOf(theirs1)
    }

    "ConversationContext accepts empty messages" {
        val ctx = ConversationContext(PartnerId("p"), emptyList())
        ctx.myMessages.shouldBeEmpty()
        ctx.theirMessages.shouldBeEmpty()
    }
})

private fun mineMsg(
    id: String,
    content: String,
) = Message.Mine(
    id = MessageId(id),
    content = content,
    sentAt = Instant.parse("2025-01-01T00:00:00Z"),
)

private fun theirsMsg(
    id: String,
    content: String,
    name: String,
) = Message.Theirs(
    id = MessageId(id),
    content = content,
    sentAt = Instant.parse("2025-01-01T00:00:00Z"),
    displayName = name,
)

private fun <T> List<T>.shouldBeEmpty() {
    if (isNotEmpty()) error("expected empty list, was $this")
}
