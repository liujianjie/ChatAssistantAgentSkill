package com.stylemirror.domain.candidate

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CandidateTest {
    @Test
    fun `optional fields default to null when omitted`() {
        val c = Candidate(text = "hi")
        c.rationale shouldBe null
        c.tokens shouldBe null
    }

    @Test
    fun `data class equality includes every field`() {
        val a = Candidate(text = "hi")
        val b = Candidate(text = "hi", rationale = "matches user's terseness")
        (a == b) shouldBe false
        (a == Candidate(text = "hi")) shouldBe true
    }
}
