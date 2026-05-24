package com.stylemirror.feature.imports.cleaning

/**
 * Immutable rule set for [MessageCleaner].
 *
 * Rules may vary by import source type:
 *  - [FilterPattern.type] == EXACT_CONTENT — entire content must match literally
 *  - [FilterPattern.type] == REGEX — content is matched against a compiled regex
 *
 * @param mergeAdjacentSameSpeaker When true, consecutive [RawMessage]s with the
 *   same non-null [RawMessage.rawSpeakerLabel] are joined with a space.
 * @param filterPatterns Patterns matching messages that should be removed entirely
 *   (transfer receipts, red packets, link cards, system notices).
 * @param normalizeEmojiWhitespace When true, zero-width-space and variation-selector
 *   characters surrounding emoji are removed.
 */
data class CleaningRules(
    val mergeAdjacentSameSpeaker: Boolean = true,
    val filterPatterns: List<FilterPattern> = emptyList(),
    val normalizeEmojiWhitespace: Boolean = true,
) {
    data class FilterPattern(
        val pattern: String,
        val type: PatternType = PatternType.EXACT_CONTENT,
    )

    enum class PatternType { EXACT_CONTENT, REGEX }
}
