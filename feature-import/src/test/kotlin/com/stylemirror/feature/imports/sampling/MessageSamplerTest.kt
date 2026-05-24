package com.stylemirror.feature.imports.sampling

import com.stylemirror.feature.imports.alignment.AlignedMessage
import com.stylemirror.feature.imports.alignment.SpeakerLabel
import com.stylemirror.feature.imports.source.RawMessage
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private fun me(
    content: String,
    idx: Int,
) = AlignedMessage(
    rawMessage = RawMessage(rawSpeakerLabel = "我", content = content, timestampHint = null, sourceIndex = idx),
    speaker = SpeakerLabel.ME,
    displayName = null,
)

private fun theirs(
    content: String,
    idx: Int,
) = AlignedMessage(
    rawMessage = RawMessage(rawSpeakerLabel = "对方", content = content, timestampHint = null, sourceIndex = idx),
    speaker = SpeakerLabel.THEIRS,
    displayName = "对方",
)

class MessageSamplerTest : StringSpec({

    // ---- filter: only Me messages -------------------------------------------

    "sample filters out THEIRS messages" {
        val sampler = MessageSampler(maxSamples = 100)
        val msgs =
            listOf(
                me("a", 0),
                theirs("b", 1),
                me("c", 2),
                theirs("d", 3),
            )
        val result = sampler.sample(msgs)
        result.totalSampled shouldBe 2
        result.myMessages.map { it.content } shouldBe listOf("a", "c")
    }

    "all-THEIRS input produces empty ProfilingInput" {
        val sampler = MessageSampler()
        val msgs = listOf(theirs("x", 0), theirs("y", 1))
        val result = sampler.sample(msgs)
        result.totalSampled shouldBe 0
        result.totalAvailable shouldBe 0
        result.myMessages.shouldHaveSize(0)
    }

    "empty input produces empty ProfilingInput" {
        MessageSampler().sample(emptyList()).totalSampled shouldBe 0
    }

    // ---- maxSamples cap -----------------------------------------------------

    "returns all messages when count <= maxSamples" {
        val sampler = MessageSampler(maxSamples = 10)
        val msgs = (0 until 8).map { me("msg$it", it) }
        val result = sampler.sample(msgs)
        result.totalSampled shouldBe 8
        result.totalAvailable shouldBe 8
    }

    "caps at maxSamples when count exceeds limit" {
        val sampler = MessageSampler(maxSamples = 100)
        val msgs = (0 until 500).map { me("msg$it", it) }
        val result = sampler.sample(msgs)
        result.totalSampled shouldBe 100
        result.totalAvailable shouldBe 500
    }

    "default maxSamples is 2000" {
        MessageSampler.DEFAULT_MAX_SAMPLES shouldBe 2000
    }

    // ---- evenly-spaced sampling distribution --------------------------------

    "evenlySpaced picks first and last elements from a list" {
        val sampler = MessageSampler()
        val list = (0..99).toList()
        val result = sampler.evenlySpaced(list, 10)
        result shouldHaveSize 10
        result.first() shouldBe 0 // first bucket
        result.last() shouldBe 90 // close to end, not past it
    }

    "evenlySpaced distributes across timeline (no recency bias)" {
        val sampler = MessageSampler(maxSamples = 10)
        val msgs = (0 until 100).map { me("msg$it", it) }
        val result = sampler.sample(msgs)

        // First sampled sourceIndex should be near 0, last near 90
        val indices = result.myMessages.map { it.sourceIndex }
        withClue("first sampled index should be in the first third") {
            (indices.first() <= 33) shouldBe true
        }
        withClue("last sampled index should be in the last third") {
            (indices.last() > 66) shouldBe true
        }
    }

    "evenlySpaced with n == list.size returns full list" {
        val sampler = MessageSampler()
        val list = listOf(1, 2, 3, 4, 5)
        sampler.evenlySpaced(list, 5) shouldBe list
    }

    "evenlySpaced with n > list.size returns full list" {
        val sampler = MessageSampler()
        val list = listOf("a", "b")
        sampler.evenlySpaced(list, 10) shouldBe list
    }

    // ---- multi-source aggregation (3 partners) ------------------------------

    "sampling 3 partner subsets each stays under maxSamples" {
        val sampler = MessageSampler(maxSamples = 50)

        fun partnerMsgs(partnerId: Int) = (0 until 200).map { me("partner$partnerId-msg$it", partnerId * 200 + it) }

        for (partner in 0..2) {
            val result = sampler.sample(partnerMsgs(partner))
            withClue("partner $partner should not exceed maxSamples") {
                (result.totalSampled <= 50) shouldBe true
            }
            result.totalAvailable shouldBe 200
        }
    }

    // ---- ProfilingInput invariants ------------------------------------------

    "ProfilingInput myMessages.size == totalSampled invariant holds" {
        val sampler = MessageSampler(maxSamples = 5)
        val msgs = (0 until 20).map { me("m$it", it) }
        val result = sampler.sample(msgs)
        result.myMessages.size shouldBe result.totalSampled
    }

    "ProfilingInput totalAvailable >= totalSampled always" {
        val sampler = MessageSampler(maxSamples = 3)
        val msgs = (0 until 10).map { me("x$it", it) }
        val result = sampler.sample(msgs)
        (result.totalAvailable >= result.totalSampled) shouldBe true
    }
})
