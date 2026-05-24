package com.stylemirror.feature.imports.sampling

import com.stylemirror.feature.imports.alignment.AlignedMessage
import com.stylemirror.feature.imports.alignment.SpeakerLabel

/**
 * Aggregates [AlignedMessage] lists into a [ProfilingInput] safe for
 * [PersonaProfiler] (T14).
 *
 * ## What it does
 *
 * 1. **Filter** — retains only messages where [SpeakerLabel] == [SpeakerLabel.ME].
 * 2. **Sample** — if the count exceeds [maxSamples], trims to [maxSamples] using
 *    evenly-spaced index sampling so the full conversation timeline is represented
 *    (not just the most recent messages).
 *
 * ## Why evenly-spaced sampling
 *
 * Recency-biased sampling (take-last-N) over-represents recent conversations.
 * Style may shift over time; a broad sample gives the profiler a truer picture.
 *
 * @param maxSamples Hard upper bound on [ProfilingInput.totalSampled].
 *   Default 2000 keeps LLM token cost reasonable at typical prompt sizes
 *   (~60 chars / message → ~120k chars → within DeepSeek 128k context).
 */
class MessageSampler(val maxSamples: Int = DEFAULT_MAX_SAMPLES) {
    fun sample(messages: List<AlignedMessage>): ProfilingInput {
        require(maxSamples > 0) { "maxSamples must be > 0, was $maxSamples" }

        val mine =
            messages
                .filter { it.speaker == SpeakerLabel.ME }
                .map { aligned ->
                    SampledMessage(
                        content = aligned.rawMessage.content,
                        timestampHint = aligned.rawMessage.timestampHint,
                        sourceIndex = aligned.rawMessage.sourceIndex,
                    )
                }

        val totalAvailable = mine.size
        val sampled =
            if (totalAvailable <= maxSamples) {
                mine
            } else {
                evenlySpaced(mine, maxSamples)
            }

        return ProfilingInput(
            myMessages = sampled,
            totalSampled = sampled.size,
            totalAvailable = totalAvailable,
        )
    }

    /**
     * Picks [n] items from [list] at evenly-spaced indices.
     *
     * For n == 1 the middle element is returned; for n == list.size every
     * element is returned. The result preserves the original order.
     */
    internal fun <T> evenlySpaced(
        list: List<T>,
        n: Int,
    ): List<T> {
        if (n >= list.size) return list
        val step = list.size.toDouble() / n
        return (0 until n).map { i -> list[(i * step).toInt().coerceAtMost(list.size - 1)] }
    }

    companion object {
        const val DEFAULT_MAX_SAMPLES: Int = 2000
    }
}
