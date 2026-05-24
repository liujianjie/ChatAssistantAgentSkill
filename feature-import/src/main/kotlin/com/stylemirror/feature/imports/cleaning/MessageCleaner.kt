package com.stylemirror.feature.imports.cleaning

import com.stylemirror.feature.imports.source.RawMessage

/**
 * Applies a [CleaningRules] pipeline to a list of [RawMessage]s.
 *
 * Pipeline stages (in order):
 *  1. **Filter** — remove messages matching any [CleaningRules.filterPatterns].
 *  2. **Merge** — join consecutive messages that share the same non-null
 *     [RawMessage.rawSpeakerLabel] into one message (content joined with " ").
 *  3. **Normalize emoji whitespace** — strip zero-width spaces and variation
 *     selectors that accumulate in copy-pasted chat text.
 *
 * [sourceIndex] on merged messages is set to the index of the *first* message
 * in the merged group, preserving chronological provenance.
 */
class MessageCleaner(val rules: CleaningRules = CleaningRulesLoader.loadDefault()) {
    fun clean(messages: List<RawMessage>): List<RawMessage> {
        var result = messages
        result = filter(result)
        if (rules.mergeAdjacentSameSpeaker) result = merge(result)
        if (rules.normalizeEmojiWhitespace) result = normalizeEmoji(result)
        return result
    }

    private fun filter(messages: List<RawMessage>): List<RawMessage> {
        if (rules.filterPatterns.isEmpty()) return messages
        val exactPatterns =
            rules.filterPatterns
                .filter { it.type == CleaningRules.PatternType.EXACT_CONTENT }
                .map { it.pattern }
        val regexPatterns =
            rules.filterPatterns
                .filter { it.type == CleaningRules.PatternType.REGEX }
                .map { Regex(it.pattern) }
        return messages.filter { msg ->
            val matchesExact = exactPatterns.any { msg.content.contains(it) }
            val matchesRegex = regexPatterns.any { it.containsMatchIn(msg.content) }
            !matchesExact && !matchesRegex
        }
    }

    private fun merge(messages: List<RawMessage>): List<RawMessage> {
        if (messages.isEmpty()) return messages
        val result = mutableListOf<RawMessage>()
        var current = messages[0]

        for (i in 1 until messages.size) {
            val next = messages[i]
            val canMerge =
                current.rawSpeakerLabel != null &&
                    current.rawSpeakerLabel == next.rawSpeakerLabel

            current =
                if (canMerge) {
                    current.copy(content = "${current.content} ${next.content}")
                } else {
                    result += current
                    next
                }
        }
        result += current
        return result
    }

    private fun normalizeEmoji(messages: List<RawMessage>): List<RawMessage> =
        messages.map { msg ->
            val cleaned =
                msg.content
                    .replace("​", "") // zero-width space
                    .replace("", "") // BOM / zero-width no-break space
                    .replace(Regex("[︎️]"), "") // variation selectors VS-15/16
                    .trim()
            if (cleaned == msg.content) msg else msg.copy(content = cleaned)
        }
}
