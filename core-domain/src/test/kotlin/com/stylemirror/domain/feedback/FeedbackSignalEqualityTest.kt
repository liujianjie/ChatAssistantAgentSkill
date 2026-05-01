package com.stylemirror.domain.feedback

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class FeedbackSignalEqualityTest : StringSpec({

    val now = Instant.parse("2025-01-01T00:00:00Z")
    val cid = CandidateId("c1")

    "Adopt equality, copy, hashCode" {
        val a = FeedbackSignal.Adopt(cid, 1, now)
        val b = FeedbackSignal.Adopt(cid, 1, now)
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
        a.copy(fingerprintVersion = 2).fingerprintVersion shouldBe 2
    }

    "Modify equality, copy" {
        val a = FeedbackSignal.Modify(cid, 1, now, "edited")
        val b = FeedbackSignal.Modify(cid, 1, now, "edited")
        a shouldBe b
        a.copy(editedContent = "other").editedContent shouldBe "other"
        a shouldNotBe a.copy(editedContent = "different")
    }

    "Discard equality, copy" {
        val a = FeedbackSignal.Discard(cid, 1, now, DiscardReason.OFF_STYLE)
        a.copy(reason = DiscardReason.TOO_LONG).reason shouldBe DiscardReason.TOO_LONG
        a shouldBe FeedbackSignal.Discard(cid, 1, now, DiscardReason.OFF_STYLE)
    }

    "CandidateId equality" {
        CandidateId("c1") shouldBe CandidateId("c1")
        CandidateId("c1") shouldNotBe CandidateId("c2")
    }
})
