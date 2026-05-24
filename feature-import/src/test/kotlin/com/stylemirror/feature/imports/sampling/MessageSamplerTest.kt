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

    // ---- char budget cap (the timeout root-cause fix) -----------------------

    "sample respects maxCharBudget by dropping to evenly-spaced N within budget" {
        // 100 messages × 100 chars each = 10000 total; budget 1000 → ~10 keep.
        val sampler = MessageSampler(maxSamples = 1000, maxCharBudget = 1000)
        val msgs = (0 until 100).map { me("x".repeat(100), it) }
        val result = sampler.sample(msgs)
        result.myMessages.sumOf { it.content.length }.let { total ->
            withClue("kept total chars must fit budget") { (total <= 1000) shouldBe true }
        }
        result.totalAvailable shouldBe 100
        // We must keep at least 1, well below 100, and roughly close to budget/100 = 10.
        (result.totalSampled in 5..20) shouldBe true
    }

    "sample within budget keeps all messages" {
        val sampler = MessageSampler(maxSamples = 1000, maxCharBudget = 10_000)
        val msgs = (0 until 50).map { me("xx", it) }
        val result = sampler.sample(msgs)
        result.totalSampled shouldBe 50
    }

    "single message longer than maxSingleLength is truncated before sampling" {
        val sampler = MessageSampler(maxSingleLength = 10, maxSamples = 100, maxCharBudget = 10_000)
        val msgs = listOf(me("0123456789ABCDE", 0)) // 15 chars
        val result = sampler.sample(msgs)
        result.myMessages.single().content shouldBe "0123456789"
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

    // ---- time spread (no recency bias) --------------------------------------

    "sampling distributes across timeline (no recency bias) when budget tight" {
        val sampler = MessageSampler(maxSamples = 10, maxCharBudget = 10_000)
        val msgs = (0 until 100).map { me("msg$it", it) }
        val result = sampler.sample(msgs)

        val indices = result.myMessages.map { it.sourceIndex }
        withClue("first sampled index should be in the first third") {
            (indices.first() <= 33) shouldBe true
        }
        withClue("last sampled index should be in the last third") {
            (indices.last() > 66) shouldBe true
        }
    }

    // ---- length preference within buckets -----------------------------------

    "long messages are preferred over short ones within the same time bucket" {
        // 30 messages: even indices are short (3 chars), odd are long (50 chars).
        // Budget tight (200 chars) → only ~6 messages fit. Sampler should
        // prefer the long ones since they carry more style signal.
        val sampler = MessageSampler(maxSamples = 100, maxCharBudget = 200)
        val msgs =
            (0 until 30).map { i ->
                val text = if (i % 2 == 0) "嗯哈对" else "x".repeat(50)
                me(text, i)
            }
        val result = sampler.sample(msgs)
        val longCount = result.myMessages.count { it.content.length == 50 }
        val shortCount = result.myMessages.count { it.content.length == 3 }
        withClue("long messages should outnumber short ones at tight budget: long=$longCount short=$shortCount") {
            (longCount > shortCount) shouldBe true
        }
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
