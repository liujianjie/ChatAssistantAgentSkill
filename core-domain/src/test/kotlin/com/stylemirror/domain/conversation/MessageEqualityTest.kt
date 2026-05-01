package com.stylemirror.domain.conversation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

/**
 * Exercises auto-generated members of Message.Mine / Message.Theirs so the
 * conversation package's coverage doesn't drift below threshold purely because
 * of synthetic copy/equals/component methods.
 */
class MessageEqualityTest : StringSpec({

    val now = Instant.parse("2025-01-01T00:00:00Z")
    val later = Instant.parse("2025-01-01T00:05:00Z")

    "Message.Mine equality, copy, hashCode, toString" {
        val a = Message.Mine(MessageId("m1"), "hi", now)
        val b = Message.Mine(MessageId("m1"), "hi", now)
        val c = a.copy(content = "hey")
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
        a shouldNotBe c
        a.toString().contains("m1") shouldBe true
    }

    "Message.Theirs equality, copy" {
        val a = Message.Theirs(MessageId("t1"), "hello", now, "Alex")
        val b = Message.Theirs(MessageId("t1"), "hello", now, "Alex")
        a shouldBe b
        a.copy(displayName = "Sam").displayName shouldBe "Sam"
        a.copy(sentAt = later).sentAt shouldBe later
    }

    "ConversationContext equality, copy" {
        val msgs = listOf(Message.Mine(MessageId("m1"), "x", now))
        val ctx1 = ConversationContext(PartnerId("p"), msgs)
        val ctx2 = ConversationContext(PartnerId("p"), msgs)
        ctx1 shouldBe ctx2
        ctx1.copy(partnerId = PartnerId("q")).partnerId shouldBe PartnerId("q")
    }

    "MessageId / PartnerId equality and toString" {
        MessageId("a") shouldBe MessageId("a")
        MessageId("a") shouldNotBe MessageId("b")
        PartnerId("p").toString().contains("p") shouldBe true
    }
})
