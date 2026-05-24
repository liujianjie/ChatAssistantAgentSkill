package com.stylemirror.feature.imports.alignment

import com.stylemirror.feature.imports.source.RawMessage
import java.util.Locale

/**
 * Resolves the speaker of each [RawMessage] to [SpeakerLabel.ME] or
 * [SpeakerLabel.THEIRS] using the user's declared alias set.
 *
 * ## Algorithm (highest-priority first)
 *
 * 1. **Token match (case-insensitive)** — tokenize both the label and each
 *    alias by stripping all non-letter characters (digits, whitespace,
 *    punctuation, emoji, symbols), lowercase ASCII, then check whether any
 *    label-token equals any alias-token. → [SpeakerLabel.ME].
 * 2. **Explicit non-alias** — if `rawSpeakerLabel` is non-null and no token
 *    matches → [SpeakerLabel.THEIRS], with `displayName = rawSpeakerLabel`.
 * 3. **Bare line inheritance** — if `rawSpeakerLabel` is null, inherit the
 *    previous message's [SpeakerLabel]. First message defaults to
 *    [SpeakerLabel.THEIRS] (conservative).
 *
 * ## Why token match (not substring)
 *
 * Substring would let alias "我" false-match label "我们" — too aggressive.
 * Token match still handles the user's real complaints:
 *   - `"张三 13800138000"` tokens → `["张三"]` → matches alias `"张三"`
 *   - `"我(admin)"` tokens → `["我", "admin"]` → matches alias `"我"`
 *   - `"Lily🌿"` tokens → `["lily"]` → matches alias `"Lily"`
 * while preserving CJK word integrity (no character-level splitting).
 */
class SpeakerAligner(val myAliases: Set<String>) {
    private val aliasTokens: Set<String> =
        myAliases.flatMap { tokenize(it) }.toSet()

    fun align(messages: List<RawMessage>): List<AlignedMessage> {
        var lastSpeaker: SpeakerLabel = SpeakerLabel.THEIRS
        return messages.map { msg ->
            val aligned = classify(msg, lastSpeaker)
            lastSpeaker = aligned.speaker
            aligned
        }
    }

    private fun classify(
        msg: RawMessage,
        lastSpeaker: SpeakerLabel,
    ): AlignedMessage {
        val label = msg.rawSpeakerLabel
        return when {
            label == null ->
                AlignedMessage(
                    rawMessage = msg,
                    speaker = lastSpeaker,
                    displayName = null,
                )

            tokenize(label).any { it in aliasTokens } ->
                AlignedMessage(rawMessage = msg, speaker = SpeakerLabel.ME, displayName = null)

            else ->
                AlignedMessage(rawMessage = msg, speaker = SpeakerLabel.THEIRS, displayName = label)
        }
    }

    companion object {
        /** Fraction of messages that may be mis-aligned before a corpus is rejected. */
        const val MAX_ERROR_RATE: Double = 0.02

        /** Splits on any run of non-letter characters (Unicode-aware). */
        private val NON_LETTER_REGEX = Regex("[^\\p{L}]+")

        internal fun tokenize(s: String): List<String> =
            s.split(NON_LETTER_REGEX)
                .filter { it.isNotEmpty() }
                .map { it.lowercase(Locale.ROOT) }
    }
}
