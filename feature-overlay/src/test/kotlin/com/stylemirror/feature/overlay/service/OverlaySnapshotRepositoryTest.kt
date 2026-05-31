package com.stylemirror.feature.overlay.service

import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.conversation.MessageId
import com.stylemirror.domain.conversation.PartnerId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Instant

private fun ctxOf(text: String): ConversationContext =
    ConversationContext(
        partnerId = PartnerId("p"),
        messages = listOf(Message.Mine(MessageId("m-$text"), text, Instant.parse("2026-05-31T10:00:00Z"))),
    )

class OverlaySnapshotRepositoryTest : StringSpec({

    beforeEach { OverlaySnapshotRepository.resetForTest() }

    "snapshot is null before any publish" {
        OverlaySnapshotRepository.snapshot().shouldBeNull()
    }

    "snapshot returns the most recent published context" {
        val a = ctxOf("a")
        val b = ctxOf("b")
        OverlaySnapshotRepository.publish(a)
        OverlaySnapshotRepository.publish(b)
        OverlaySnapshotRepository.snapshot().shouldNotBeNull() shouldBe b
    }

    "late subscribers receive replayed latest via flow" {
        runTest {
            val a = ctxOf("hello")
            OverlaySnapshotRepository.publish(a)
            // first() suspends until at least one value is replayed
            OverlaySnapshotRepository.latest.first() shouldBe a
        }
    }

    "resetForTest clears replay cache" {
        OverlaySnapshotRepository.publish(ctxOf("x"))
        OverlaySnapshotRepository.resetForTest()
        OverlaySnapshotRepository.snapshot().shouldBeNull()
    }
})
