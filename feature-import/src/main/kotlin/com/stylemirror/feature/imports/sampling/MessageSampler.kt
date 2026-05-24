package com.stylemirror.feature.imports.sampling

import com.stylemirror.feature.imports.alignment.AlignedMessage
import com.stylemirror.feature.imports.alignment.SpeakerLabel

/**
 * Aggregates [AlignedMessage] lists into a [ProfilingInput] safe for
 * [PersonaProfiler] (画像 v2).
 *
 * ## Pipeline
 *
 * 1. **Filter speaker** — keep only [SpeakerLabel.ME].
 * 2. **Drop pure noise** — [NoiseFilter] removes pure punctuation /
 *    whitespace / emoji-only / single-char stopword messages. Conservative
 *    by design (does NOT remove "确实" / "离谱" / "哈哈哈" — those are style).
 * 3. **Truncate single** — clip any message longer than [maxSingleLength].
 * 4. **Bucket by time** — the surviving messages are split into [TIME_BUCKETS]
 *    equal-width buckets so a multi-year conversation is represented across
 *    the timeline (not biased to the most recent / largest cluster).
 * 5. **Within each bucket, sort by length DESC** — prefer messages with more
 *    content, since a 30-char "我觉得这事吧其实没那么..." carries more style
 *    signal than another "好的".
 * 6. **Round-robin pick** — take one from each bucket in turn, draining the
 *    longest first, until either [maxSamples] or [maxCharBudget] is hit.
 * 7. **Restore time order** — sort the picked set by sourceIndex so the
 *    profiler sees them chronologically.
 *
 * ## Why bucket-by-time + length-DESC (instead of evenly-spaced or top-N-longest)
 *
 * - Pure evenly-spaced ignored content; budget-trim phase tended to drop
 *   long messages (they used more budget per slot) — backwards from what
 *   we want for style signal.
 * - Pure top-N-longest collapses to one chat era and loses long-arc style
 *   drift.
 * - The hybrid keeps long-message preference WITHIN each bucket and
 *   guarantees time spread ACROSS buckets.
 *
 * @param maxSamples Hard upper bound on message count.
 * @param maxCharBudget Hard upper bound on total characters across all kept
 *   messages. Default 8000 ≈ 4000 中文 tokens (leaves room for prompt
 *   scaffolding + LLM output).
 * @param maxSingleLength Per-message cap before sampling (default 400).
 */
class MessageSampler(
    val maxSamples: Int = DEFAULT_MAX_SAMPLES,
    val maxCharBudget: Int = DEFAULT_MAX_CHAR_BUDGET,
    val maxSingleLength: Int = DEFAULT_MAX_SINGLE_LENGTH,
) {
    init {
        require(maxSamples > 0) { "maxSamples must be > 0, was $maxSamples" }
        require(maxCharBudget > 0) { "maxCharBudget must be > 0, was $maxCharBudget" }
        require(maxSingleLength > 0) { "maxSingleLength must be > 0, was $maxSingleLength" }
    }

    fun sample(messages: List<AlignedMessage>): ProfilingInput {
        val candidates =
            messages
                .asSequence()
                .filter { it.speaker == SpeakerLabel.ME }
                .filterNot { NoiseFilter.isNoise(it.rawMessage.content) }
                .map { aligned ->
                    val raw = aligned.rawMessage.content
                    val clipped = if (raw.length > maxSingleLength) raw.take(maxSingleLength) else raw
                    SampledMessage(
                        content = clipped,
                        timestampHint = aligned.rawMessage.timestampHint,
                        sourceIndex = aligned.rawMessage.sourceIndex,
                    )
                }
                .toList()

        val totalAvailable = candidates.size
        if (totalAvailable == 0) {
            return ProfilingInput(myMessages = emptyList(), totalSampled = 0, totalAvailable = 0)
        }
        val picked = bucketedLengthPreferred(candidates, maxSamples, maxCharBudget)
        // Restore chronological order (sourceIndex monotonic with time).
        val ordered = picked.sortedBy { it.sourceIndex }
        return ProfilingInput(
            myMessages = ordered,
            totalSampled = ordered.size,
            totalAvailable = totalAvailable,
        )
    }

    /**
     * Splits [candidates] into [TIME_BUCKETS] equal-width buckets by their
     * position in the input list (proxy for time when sourceIndex is
     * monotonic), sorts each bucket by content length DESC, then round-robins
     * across buckets — taking the longest unused message from each bucket per
     * round — until either [sizeCap] messages or [charBudget] characters are
     * collected.
     */
    @Suppress("LoopWithTooManyJumpStatements")
    internal fun bucketedLengthPreferred(
        candidates: List<SampledMessage>,
        sizeCap: Int,
        charBudget: Int,
    ): List<SampledMessage> {
        val n = candidates.size
        val numBuckets = minOf(TIME_BUCKETS, n)
        val bucketSize = n.toDouble() / numBuckets
        val buckets =
            (0 until numBuckets).map { i ->
                val start = (i * bucketSize).toInt()
                val end = ((i + 1) * bucketSize).toInt().coerceAtMost(n)
                candidates.subList(start, end).sortedByDescending { it.content.length }
            }
        val cursors = IntArray(numBuckets)
        val out = ArrayList<SampledMessage>(minOf(sizeCap, n))
        var charsUsed = 0
        var stillHaveAny = true
        while (out.size < sizeCap && stillHaveAny) {
            stillHaveAny = false
            for (b in 0 until numBuckets) {
                if (cursors[b] >= buckets[b].size) continue
                val candidate = buckets[b][cursors[b]]
                val candLen = candidate.content.length
                // If even the smallest remaining message in any bucket would
                // overflow the budget, stop. (Conservative — doesn't try
                // skipping; just stops to keep the algorithm predictable.)
                if (charsUsed + candLen > charBudget) {
                    cursors[b] = buckets[b].size // mark this bucket exhausted
                    continue
                }
                out += candidate
                charsUsed += candLen
                cursors[b]++
                stillHaveAny = true
                if (out.size >= sizeCap) return out
            }
        }
        return out
    }

    companion object {
        const val DEFAULT_MAX_SAMPLES: Int = 2000
        const val DEFAULT_MAX_CHAR_BUDGET: Int = 8_000
        const val DEFAULT_MAX_SINGLE_LENGTH: Int = 400
        const val TIME_BUCKETS: Int = 10
    }
}
