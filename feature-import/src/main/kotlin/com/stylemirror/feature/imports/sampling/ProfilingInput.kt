package com.stylemirror.feature.imports.sampling

import java.time.Instant

/**
 * A single user ("Me") message ready for style profiling.
 *
 * Deliberately stripped of any [com.stylemirror.feature.imports.alignment.SpeakerLabel]
 * or display-name fields so downstream [PersonaProfiler] (T14) cannot
 * accidentally include other-party content in the LLM payload.
 *
 * @property content Message body, already cleaned by [MessageCleaner].
 * @property timestampHint Optional parsed timestamp from the source.
 * @property sourceIndex Original line index for provenance / deduplication.
 */
data class SampledMessage(
    val content: String,
    val timestampHint: Instant?,
    val sourceIndex: Int,
)

/**
 * The sole input to [PersonaProfiler] (T14).
 *
 * **Privacy red line (compile-time)**: this class only carries [myMessages] —
 * there is no field for other-party messages. Type-level enforcement means
 * even a future contributor cannot accidentally pass Theirs content to the
 * profiling LLM call without changing this type.
 *
 * @property myMessages Me's messages after sampling.
 * @property totalSampled Number of messages returned in [myMessages].
 * @property totalAvailable Total Me messages available before sampling.
 *   When [totalAvailable] > [totalSampled], the corpus was trimmed.
 */
data class ProfilingInput(
    val myMessages: List<SampledMessage>,
    val totalSampled: Int,
    val totalAvailable: Int,
) {
    init {
        require(myMessages.size == totalSampled) {
            "myMessages.size (${myMessages.size}) must equal totalSampled ($totalSampled)"
        }
        require(totalAvailable >= totalSampled) {
            "totalAvailable ($totalAvailable) must be >= totalSampled ($totalSampled)"
        }
    }
}
