package com.stylemirror.feature.imports.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * [ImportSource] backed by plain UTF-8 text — either pasted directly or
 * read from a `.txt` file.
 *
 * ## Supported input formats (in detection priority order)
 *
 * 1. **Timestamped** — `"YYYY-MM-DD HH:mm 昵称：内容"` or
 *    `"YYYY-MM-DD HH:mm:ss 昵称：内容"` — the timestamp prefix is stripped
 *    and stored in [RawMessage.timestampHint].
 * 2. **Prefixed** — `"昵称：内容"` or `"昵称: 内容"` (full-width and ASCII
 *    colons accepted). The prefix becomes [RawMessage.rawSpeakerLabel].
 * 3. **Bare line** — no prefix detected; [RawMessage.rawSpeakerLabel] is null.
 *    These are treated as continuation of the previous message by the cleaning
 *    pipeline (T11).
 *
 * Blank lines are skipped. The stream is lazy — only the current line is held
 * in memory at any time, so 10k+ message corpora are handled without heap
 * pressure.
 *
 * @param text The raw string to parse. Line separator is `\n` or `\r\n`.
 */
class PlainTextImportSource(private val text: String) : ImportSource {
    override fun stream(): Flow<RawMessage> =
        flow {
            var sourceIndex = 0
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    emit(parseLine(line, sourceIndex++))
                }
        }

    companion object {
        // "2024-01-15 14:30 " or "2024-01-15 14:30:00 "
        private val TIMESTAMP_PATTERN_LONG =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val TIMESTAMP_PATTERN_SHORT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        // Captures: optional timestamp prefix, optional speaker, content.
        // Speaker is bounded to ≤ 20 chars (same rule as PasteInput).
        private val TIMESTAMPED_REGEX =
            Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?)\s+([^:：\s][^:：]{0,19})[:：]\s*(.+)$""")
        private val PREFIXED_REGEX =
            Regex("""^([^:：\s][^:：]{0,19})[:：]\s*(.+)$""")

        internal fun parseLine(
            line: String,
            sourceIndex: Int,
        ): RawMessage {
            // Try timestamped format first.
            TIMESTAMPED_REGEX.matchEntire(line)?.let { m ->
                val (rawTs, speaker, content) = m.destructured
                val ts = parseTimestamp(rawTs)
                return RawMessage(
                    rawSpeakerLabel = speaker.trim(),
                    content = content.trim(),
                    timestampHint = ts,
                    sourceIndex = sourceIndex,
                )
            }

            // Try prefixed (no timestamp).
            PREFIXED_REGEX.matchEntire(line)?.let { m ->
                val (speaker, content) = m.destructured
                return RawMessage(
                    rawSpeakerLabel = speaker.trim(),
                    content = content.trim(),
                    timestampHint = null,
                    sourceIndex = sourceIndex,
                )
            }

            // Bare line — no speaker.
            return RawMessage(
                rawSpeakerLabel = null,
                content = line.trim(),
                timestampHint = null,
                sourceIndex = sourceIndex,
            )
        }

        private fun parseTimestamp(raw: String): java.time.Instant? {
            val trimmed = raw.trim()
            return try {
                LocalDateTime.parse(trimmed, TIMESTAMP_PATTERN_LONG)
                    .toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(trimmed, TIMESTAMP_PATTERN_SHORT)
                        .toInstant(ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
