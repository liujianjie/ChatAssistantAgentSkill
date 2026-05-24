package com.stylemirror.feature.imports.benchmark

import com.stylemirror.feature.imports.alignment.SpeakerAligner
import com.stylemirror.feature.imports.cleaning.MessageCleaner
import com.stylemirror.feature.imports.sampling.MessageSampler
import com.stylemirror.feature.imports.source.RawMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import kotlin.system.measureTimeMillis

/**
 * JVM-level micro benchmark for the import hot path:
 *   RawMessage[] → MessageCleaner → SpeakerAligner → MessageSampler
 *
 * Why this exists (T22 part 1):
 * The SPEC target is "10k messages cleaned & profiled ≤ 60s on device" — but
 * the LLM call dominates that 60s. The pure-CPU portion (this test) should
 * finish in a tiny fraction of that. We assert a generous 3000ms ceiling on
 * CI to catch O(n²) regressions without being flaky on slow CI runners.
 *
 * Real numbers on dev hardware are recorded in docs/perf/baseline.md.
 *
 * This is intentionally NOT a JMH harness — JMH would require a separate
 * source set and offer no value for the regression-guard goal. We just
 * time three stages with measureTimeMillis and print results.
 */
class ImportPipelineBenchmarkTest : StringSpec({

    "10k messages flow through cleaner→aligner→sampler in < 3s" {
        val messages = synthesizeMessages(count = 10_000)
        val cleaner = MessageCleaner()
        val aligner = SpeakerAligner(myAliases = setOf("我"))
        val sampler = MessageSampler(maxSamples = 2_000)

        var cleaned: List<RawMessage>
        val cleanMs =
            measureTimeMillis {
                cleaned = cleaner.clean(messages)
            }
        var aligned: List<com.stylemirror.feature.imports.alignment.AlignedMessage>
        val alignMs =
            measureTimeMillis {
                aligned = aligner.align(cleaned)
            }
        val sampleMs =
            measureTimeMillis {
                sampler.sample(aligned)
            }
        val totalMs = cleanMs + alignMs + sampleMs

        // Print so CI logs preserve a trail to update baseline.md against.
        println(
            "[bench] 10k messages: clean=${cleanMs}ms, align=${alignMs}ms, " +
                "sample=${sampleMs}ms, total=${totalMs}ms",
        )

        totalMs shouldBeLessThan TOTAL_BUDGET_MS
    }

    "1k messages flow through cleaner→aligner→sampler in < 500ms" {
        val messages = synthesizeMessages(count = 1_000)
        val cleaner = MessageCleaner()
        val aligner = SpeakerAligner(myAliases = setOf("我"))
        val sampler = MessageSampler(maxSamples = 2_000)

        val totalMs =
            measureTimeMillis {
                val cleaned = cleaner.clean(messages)
                val aligned = aligner.align(cleaned)
                sampler.sample(aligned)
            }
        println("[bench] 1k messages: total=${totalMs}ms")
        totalMs shouldBeLessThan SMALL_BUDGET_MS
    }
})

/**
 * Build a synthetic [RawMessage] list:
 *   - Alternating "我" / "对方" speaker labels so cleaner merge has work to do
 *   - Every 50th message is a system noise line that should be filtered
 *   - Content includes occasional zero-width chars to exercise normalization
 */
private fun synthesizeMessages(count: Int): List<RawMessage> {
    val sample =
        listOf(
            "你好啊",
            "在干嘛呢",
            "今天周末有空一起吃饭吗",
            "ok 好的",
            "哈哈哈太好笑了",
            "改天再约",
            "嗯",
        )
    return (0 until count).map { i ->
        val isSystem = i % 50 == 0 && i > 0
        val isMe = i % 2 == 0
        val label =
            when {
                isSystem -> null
                isMe -> "我"
                else -> "对方"
            }
        val content =
            when {
                isSystem -> "[红包]恭喜发财"
                else -> sample[i % sample.size] + (if (i % 13 == 0) "​" else "") // zero-width occasionally
            }
        RawMessage(
            rawSpeakerLabel = label,
            content = content,
            timestampHint = null,
            sourceIndex = i,
        )
    }
}

private const val TOTAL_BUDGET_MS: Long = 3_000
private const val SMALL_BUDGET_MS: Long = 500
