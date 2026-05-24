package com.stylemirror.feature.imports.sampling

/**
 * Conservative noise filter for raw user messages before profiling
 * (画像 v2 / T29.timeout follow-up).
 *
 * **What this drops** (all evaluated on the trimmed text):
 *   1. Purely whitespace strings.
 *   2. Strings whose every char is punctuation (CJK + ASCII).
 *   3. Strings containing emoji but no CJK/Latin/digit content.
 *   4. Single-character messages whose char is in [SINGLE_CHAR_STOPWORDS]
 *      (嗯/哦/啊/好/对/是/在/...) — pure response words with no style signal.
 *
 * **What this KEEPS** (deliberate, per the conservative product call):
 *   - Short but distinctive words: 「确实」「离谱」「绷不住」 — these ARE
 *     the user's style.
 *   - Repeated chars: 「哈哈哈」「呜呜呜」 — emotional cadence.
 *   - 「？？？」「!!!」 — punctuation-only? Yes that gets dropped (rule 2),
 *     but very few real signals fit this exact shape.
 *   - emoji + text: 「哈哈😂」 — keeps the textual char.
 *
 * The intent is **never to delete a real style signal**, only the obvious
 * boilerplate. Erring on the side of keeping noise; the LLM's later step
 * picks 30-80 representative samples from whatever survives.
 */
object NoiseFilter {
    /**
     * Single-character "filler" tokens that on their own carry no style
     * information. Conservative list — only includes chars whose sole
     * meaning is acknowledgement / hesitation / yes-no.
     */
    val SINGLE_CHAR_STOPWORDS: Set<String> =
        setOf(
            // 应答 / 嗯哦
            "嗯", "恩", "哦", "噢", "喔",
            "啊", "呃", "呀", "呢", "啦",
            "哈", "嘿", "嗷",
            // 是非确认
            "好", "对", "是", "行", "中", "在",
            "没", "有",
            // 中文虚词
            "了", "的", "吗", "吧", "么",
            // 单字符 ASCII 应答
            "k", "K", "y", "Y", "n", "N",
            ".", "?", "!",
        )

    private val PUNCTUATION_REGEX =
        Regex("""^[\p{Punct}\p{S}　-〿＀-￯\s]+$""")

    /**
     * @return true when the message is pure noise and should be dropped
     *   before sampling.
     */
    @Suppress("ReturnCount")
    fun isNoise(content: String): Boolean {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return true

        // Rule 1+2: pure whitespace OR pure punctuation/symbols
        if (PUNCTUATION_REGEX.matches(trimmed)) return true

        // Rule 4: single-char stopword
        if (trimmed.length == 1 && trimmed in SINGLE_CHAR_STOPWORDS) return true

        // Rule 3: contains emoji surrogate pairs but no CJK/Latin/digit
        if (hasNoTextualContent(trimmed)) return true

        return false
    }

    /**
     * True when the string contains zero CJK ideograph, Latin letter, or
     * digit. Such strings are typically all-emoji or all-punctuation.
     */
    internal fun hasNoTextualContent(text: String): Boolean =
        text.none { c ->
            c.isLetterOrDigit() ||
                (c.code in 0x4E00..0x9FFF) || // CJK Unified Ideographs
                (c.code in 0x3400..0x4DBF) // CJK Extension A
        }
}
