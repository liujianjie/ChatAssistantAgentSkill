package com.stylemirror.feature.realtime.candidate

/**
 * Strips personally-identifiable number patterns from chat text before
 * sending to an external LLM. Applied only to the "theirs" messages in the
 * prompt; user's own messages are excluded from the LLM payload entirely.
 *
 * Patterns covered (Chinese context):
 *  - Mobile phone:    1[3-9]XXXXXXXXX (11 digits starting with 1[3-9])
 *  - National ID:     18-digit number ending with a digit or 'X'/'x'
 *  - Bank card:       continuous 16–19 digit sequences (with optional spaces
 *                     or hyphens every 4 digits)
 *
 * Replacement token is [REDACTED] so downstream consumers know something was
 * removed without seeing the original value.
 */
object PrivacyGuard {
    private const val REDACTED = "[REDACTED]"

    // 11-digit mainland mobile: must start with 1[3-9]
    private val PHONE_REGEX = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")

    // 18-digit national ID: 17 digits + check digit (digit or X/x)
    private val ID_CARD_REGEX = Regex("""(?<!\d)\d{17}[\dXx](?!\d)""")

    // Bank card: 16–19 consecutive digits (plain) or groups of 4 separated by
    // space / hyphen (e.g. "6222 0000 0000 0000" or "6222-0000-0000-0000")
    private val BANK_CARD_PLAIN_REGEX = Regex("""(?<!\d)\d{16,19}(?!\d)""")
    private val BANK_CARD_GROUPED_REGEX =
        Regex("""(?<!\d)\d{4}([ -]\d{4}){3,4}(?!\d)""")

    /**
     * Returns [text] with all detectable PII number patterns replaced by
     * [REDACTED]. Order matters: grouped bank-card patterns are stripped
     * before plain-digit patterns to avoid double-replacing.
     */
    fun redact(text: String): String =
        text
            .let { BANK_CARD_GROUPED_REGEX.replace(it, REDACTED) }
            .let { BANK_CARD_PLAIN_REGEX.replace(it, REDACTED) }
            .let { ID_CARD_REGEX.replace(it, REDACTED) }
            .let { PHONE_REGEX.replace(it, REDACTED) }
}
