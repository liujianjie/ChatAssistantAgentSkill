package com.stylemirror.feature.imports.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * [ImportSource] backed by plain UTF-8 text — pasted, read from .txt/.md,
 * or extracted from .docx/.html/.pdf upstream.
 *
 * ## Supported input formats (in detection priority order)
 *
 * 1. **Inline timestamped** — `"YYYY-MM-DD HH:mm 昵称：内容"` on one line.
 * 2. **Inline prefixed** — `"昵称：内容"` (full-width or ASCII colon).
 * 3. **QQ-style header (date+name)** — header line is
 *    `"YYYY-MM-DD HH:mm:ss 昵称"` (NO colon), followed by one or more
 *    content lines until the next header or a blank-line+header boundary.
 * 4. **QQ-style header (name+time)** — header line is `"昵称 HH:mm[:ss]"`
 *    (NO colon, no date), same content semantics.
 * 5. **Bare line** — anything else (passes through with null speaker; the
 *    speaker aligner inherits from the previous message).
 *
 * ## What gets dropped at this layer
 *
 * Lines that are purely separator characters (`====`, `----`, ...) and
 * QQ chat-export metadata headers (`消息分组：xxx`, `消息对象：xxx`,
 * `消息记录...`) are skipped before parsing — otherwise their colons would
 * be misidentified as inline-prefix delimiters and pollute the speaker pool.
 *
 * @param text The raw string to parse. Line separator is `\n` or `\r\n`.
 */
class PlainTextImportSource(private val text: String) : ImportSource {
    override fun stream(): Flow<RawMessage> =
        flow {
            parse(text.lineSequence().toList()).forEach { emit(it) }
        }

    companion object {
        // Inline patterns (one line = one message, with colon delimiter).
        private val INLINE_TIMESTAMPED_REGEX =
            Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?)\s+([^:：\s][^:：]{0,19})[:：]\s*(.+)$""")
        private val INLINE_PREFIXED_REGEX =
            Regex("""^([^:：\s][^:：]{0,19})[:：]\s*(.+)$""")

        // QQ-style header patterns (one line = header, content follows on
        // subsequent non-empty lines). NO colon allowed in either form —
        // those would be picked up by the inline patterns above.
        private val HEADER_DATE_NAME =
            Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?)\s+([^:：\d][^:：]{0,40})\s*$""")
        private val HEADER_NAME_TIME =
            Regex("""^([^\d:：\s][^:：]{0,40}?)\s+(\d{1,2}:\d{2}(?::\d{2})?)\s*$""")

        // Lines we drop entirely before parsing.
        private val SEPARATOR_LINE = Regex("""^[=\-~_*]{3,}\s*$""")
        private val QQ_METADATA_LINE = Regex("""^(消息分组|消息对象|消息记录).*$""")

        private val TIMESTAMP_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val TIMESTAMP_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
        internal fun parse(lines: List<String>): List<RawMessage> {
            val out = mutableListOf<RawMessage>()
            var pendingSpeaker: String? = null
            var pendingTimestamp: Instant? = null
            val pendingContent = StringBuilder()

            fun flushPending() {
                val sp = pendingSpeaker
                if (sp != null && pendingContent.isNotEmpty()) {
                    out +=
                        RawMessage(
                            rawSpeakerLabel = sp,
                            content = pendingContent.toString().trim(),
                            timestampHint = pendingTimestamp,
                            sourceIndex = out.size,
                        )
                }
                pendingSpeaker = null
                pendingTimestamp = null
                pendingContent.clear()
            }

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || SEPARATOR_LINE.matches(line) || QQ_METADATA_LINE.matches(line)) continue

                val inlineTs = INLINE_TIMESTAMPED_REGEX.matchEntire(line)
                // QQ-style headers must run before INLINE_PREFIXED — that pattern
                // is greedy enough to misread a timestamp's colon as the
                // speaker/content delimiter (e.g. "指着太阳公公说日 12:38:26"
                // would parse as speaker="指着太阳公公说日 12" content="38:26").
                val headerDateName =
                    if (inlineTs == null) HEADER_DATE_NAME.matchEntire(line) else null
                val headerNameTime =
                    if (inlineTs == null && headerDateName == null) {
                        HEADER_NAME_TIME.matchEntire(line)
                    } else {
                        null
                    }
                val inlinePrefixed =
                    if (inlineTs == null && headerDateName == null && headerNameTime == null) {
                        INLINE_PREFIXED_REGEX.matchEntire(line)
                    } else {
                        null
                    }

                when {
                    inlineTs != null -> {
                        flushPending()
                        val (rawTs, sp, ct) = inlineTs.destructured
                        out +=
                            RawMessage(
                                rawSpeakerLabel = sp.trim(),
                                content = ct.trim(),
                                timestampHint = parseTimestamp(rawTs),
                                sourceIndex = out.size,
                            )
                    }
                    headerDateName != null -> {
                        flushPending()
                        val (rawTs, sp) = headerDateName.destructured
                        pendingSpeaker = sp.trim()
                        pendingTimestamp = parseTimestamp(rawTs)
                    }
                    headerNameTime != null -> {
                        flushPending()
                        val (sp, _) = headerNameTime.destructured
                        pendingSpeaker = sp.trim()
                        pendingTimestamp = null
                    }
                    inlinePrefixed != null -> {
                        flushPending()
                        val (sp, ct) = inlinePrefixed.destructured
                        out +=
                            RawMessage(
                                rawSpeakerLabel = sp.trim(),
                                content = ct.trim(),
                                timestampHint = null,
                                sourceIndex = out.size,
                            )
                    }
                    pendingSpeaker != null -> {
                        if (pendingContent.isNotEmpty()) pendingContent.append('\n')
                        pendingContent.append(line)
                    }
                    else -> {
                        out +=
                            RawMessage(
                                rawSpeakerLabel = null,
                                content = line,
                                timestampHint = null,
                                sourceIndex = out.size,
                            )
                    }
                }
            }
            flushPending()
            return out
        }

        internal fun parseLine(
            line: String,
            sourceIndex: Int,
        ): RawMessage =
            INLINE_TIMESTAMPED_REGEX.matchEntire(line)?.let { m ->
                val (rawTs, sp, ct) = m.destructured
                RawMessage(
                    rawSpeakerLabel = sp.trim(),
                    content = ct.trim(),
                    timestampHint = parseTimestamp(rawTs),
                    sourceIndex = sourceIndex,
                )
            } ?: INLINE_PREFIXED_REGEX.matchEntire(line)?.let { m ->
                val (sp, ct) = m.destructured
                RawMessage(
                    rawSpeakerLabel = sp.trim(),
                    content = ct.trim(),
                    timestampHint = null,
                    sourceIndex = sourceIndex,
                )
            } ?: RawMessage(
                rawSpeakerLabel = null,
                content = line.trim(),
                timestampHint = null,
                sourceIndex = sourceIndex,
            )

        private fun parseTimestamp(raw: String): Instant? {
            val trimmed = raw.trim()
            return try {
                LocalDateTime.parse(trimmed, TIMESTAMP_LONG).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(trimmed, TIMESTAMP_SHORT).toInstant(ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
