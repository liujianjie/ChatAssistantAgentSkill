package com.stylemirror.feature.imports.source

import java.time.Instant

/**
 * A single message as it comes off the import source — before speaker
 * alignment (T12) and cleaning (T11). Every field is nullable / loose because
 * import formats vary widely; the cleaning pipeline normalises them later.
 *
 * @property rawSpeakerLabel The text prefix before the colon in "Alice: hello".
 *   Null for bare lines (no prefix detected).
 * @property content Message body, trimmed of surrounding whitespace.
 * @property timestampHint Parsed [Instant] when the source provides a
 *   timestamp (e.g. "2024-01-15 14:30"). Null when the source omits it.
 * @property sourceIndex 0-based position in the source stream, useful for
 *   provenance and debugging.
 */
data class RawMessage(
    val rawSpeakerLabel: String?,
    val content: String,
    val timestampHint: Instant?,
    val sourceIndex: Int,
)
