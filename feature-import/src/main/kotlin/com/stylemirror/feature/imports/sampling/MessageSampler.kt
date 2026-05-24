package com.stylemirror.feature.imports.sampling

import com.stylemirror.feature.imports.alignment.AlignedMessage
import com.stylemirror.feature.imports.alignment.SpeakerLabel

/**
 * Aggregates [AlignedMessage] lists into a [ProfilingInput] safe for
 * [PersonaProfiler] (T14).
 *
 * ## Pipeline
 *
 * 1. **Filter** — retain only messages where speaker == [SpeakerLabel.ME].
 * 2. **Truncate single** — clip any individual message longer than
 *    [maxSingleLength] so a single 5000-char rant doesn't dominate the budget.
 * 3. **Cap by count** — if more than [maxSamples] messages remain, take
 *    [maxSamples] evenly-spaced across the timeline (preserves long-arc style
 *    instead of recency bias).
 * 4. **Cap by characters** — if the total character count still exceeds
 *    [maxCharBudget], drop further to the largest evenly-spaced N that fits
 *    the budget. This is the **token guard** that prevents PersonaProfiler
 *    from blowing up DeepSeek's read timeout on a 700 KB chat history.
 *
 * ## Why TWO caps (count + chars)
 *
 * Either cap alone is brittle:
 * - count-only: 800 short greetings is fine, but 800 paragraph-length rants
 *   blows past the 90 s LLM read budget.
 * - chars-only: 5 long messages is well under budget but gives the profiler
 *   no breadth.
 *
 * The combination guarantees a predictable upper bound on prompt size while
 * still surfacing enough variety.
 *
 * @param maxSamples Hard upper bound on message count (default 2000).
 * @param maxCharBudget Hard upper bound on total characters across all kept
 *   messages (default 8000 ≈ 4000 中文 tokens — leaves room for prompt
 *   scaffolding + LLM output).
 * @param maxSingleLength Per-message cap; longer messages are truncated to
 *   this length before sampling (default 400 chars).
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
        val mine =
            messages
                .filter { it.speaker == SpeakerLabel.ME }
                .map { aligned ->
                    val raw = aligned.rawMessage.content
                    val clipped = if (raw.length > maxSingleLength) raw.take(maxSingleLength) else raw
                    SampledMessage(
                        content = clipped,
                        timestampHint = aligned.rawMessage.timestampHint,
                        sourceIndex = aligned.rawMessage.sourceIndex,
                    )
                }

        val totalAvailable = mine.size
        val byCount =
            if (totalAvailable <= maxSamples) mine else evenlySpaced(mine, maxSamples)
        val byBudget = trimToCharBudget(byCount, maxCharBudget)

        return ProfilingInput(
            myMessages = byBudget,
            totalSampled = byBudget.size,
            totalAvailable = totalAvailable,
        )
    }

    /**
     * Picks [n] items from [list] at evenly-spaced indices. For n == 1 the
     * middle element is returned; for n >= list.size every element is returned.
     */
    internal fun <T> evenlySpaced(
        list: List<T>,
        n: Int,
    ): List<T> {
        if (n >= list.size) return list
        val step = list.size.toDouble() / n
        return (0 until n).map { i -> list[(i * step).toInt().coerceAtMost(list.size - 1)] }
    }

    /**
     * Binary-searches the largest N for which `evenlySpaced(items, N)` fits
     * within [budget] total characters. Returns at least one element so
     * downstream code never sees an empty profile (PersonaProfiler will then
     * flag InsufficientProfile by its own threshold).
     */
    internal fun trimToCharBudget(
        items: List<SampledMessage>,
        budget: Int,
    ): List<SampledMessage> {
        if (items.isEmpty()) return items
        if (items.sumOf { it.content.length } <= budget) return items
        var lo = 1
        var hi = items.size
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            val candidate = evenlySpaced(items, mid)
            if (candidate.sumOf { it.content.length } <= budget) lo = mid else hi = mid - 1
        }
        return evenlySpaced(items, lo)
    }

    companion object {
        const val DEFAULT_MAX_SAMPLES: Int = 2000
        const val DEFAULT_MAX_CHAR_BUDGET: Int = 8_000
        const val DEFAULT_MAX_SINGLE_LENGTH: Int = 400
    }
}
