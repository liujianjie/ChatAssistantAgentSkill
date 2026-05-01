package com.stylemirror.domain.feedback

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

class FeedbackSignalTest : StringSpec({

    val now = Instant.parse("2025-01-01T00:00:00Z")
    val cid = CandidateId("c1")

    "CandidateId rejects blank" {
        shouldThrow<IllegalArgumentException> { CandidateId("") }
        shouldThrow<IllegalArgumentException> { CandidateId("   ") }
    }

    "Adopt happy path" {
        val s: FeedbackSignal = FeedbackSignal.Adopt(cid, fingerprintVersion = 3, createdAt = now)
        s.shouldBeInstanceOf<FeedbackSignal.Adopt>()
        s.candidateId shouldBe cid
        s.fingerprintVersion shouldBe 3
        s.createdAt shouldBe now
    }

    "Modify rejects empty editedContent" {
        shouldThrow<IllegalArgumentException> {
            FeedbackSignal.Modify(cid, fingerprintVersion = 1, createdAt = now, editedContent = "")
        }
    }

    "Modify happy path" {
        val s = FeedbackSignal.Modify(cid, fingerprintVersion = 1, createdAt = now, editedContent = "ok")
        s.editedContent shouldBe "ok"
    }

    "Discard records reason" {
        val s = FeedbackSignal.Discard(cid, fingerprintVersion = 2, createdAt = now, reason = DiscardReason.OFF_STYLE)
        s.reason shouldBe DiscardReason.OFF_STYLE
    }

    "exhaustive when on FeedbackSignal compiles" {
        val s: FeedbackSignal = FeedbackSignal.Adopt(cid, 1, now)
        val tag =
            when (s) {
                is FeedbackSignal.Adopt -> "adopt"
                is FeedbackSignal.Modify -> "modify"
                is FeedbackSignal.Discard -> "discard"
            }
        tag shouldBe "adopt"
    }

    "DiscardReason enumerates the documented buckets" {
        DiscardReason.values().toSet() shouldBe
            setOf(
                DiscardReason.OFF_STYLE,
                DiscardReason.OFF_TOPIC,
                DiscardReason.TOO_LONG,
                DiscardReason.TOO_SHORT,
                DiscardReason.INAPPROPRIATE,
                DiscardReason.OTHER,
            )
    }
})
