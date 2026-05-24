package com.stylemirror.feature.imports.alignment

import com.stylemirror.feature.imports.source.RawMessage

/**
 * Resolves the speaker of each [RawMessage] to [SpeakerLabel.ME] or
 * [SpeakerLabel.THEIRS] using the user's declared alias set.
 *
 * ## Algorithm (highest-priority first)
 *
 * 1. **Explicit alias match** — if `rawSpeakerLabel` is non-null and appears
 *    in [myAliases] (case-sensitive) → [SpeakerLabel.ME].
 * 2. **Explicit non-alias** — if `rawSpeakerLabel` is non-null and NOT in
 *    [myAliases] → [SpeakerLabel.THEIRS], with `displayName = rawSpeakerLabel`.
 * 3. **Bare line inheritance** — if `rawSpeakerLabel` is null, inherit the
 *    previous message's [SpeakerLabel]. If this is the first message, default
 *    to [SpeakerLabel.THEIRS] (conservative: we'd rather mis-attribute the
 *    user's own message than send a stranger's message into the style fingerprint).
 *
 * ## Onboarding integration
 *
 * The user specifies aliases during onboarding (T15). A single alias (e.g. "我")
 * is always sufficient; multiple aliases cover the cross-device / nickname-change
 * scenarios (SPEC §1.4).
 *
 * @param myAliases The set of display names / prefixes the user goes by.
 *   Matching is case-sensitive and exact. An empty set causes every message
 *   to be labelled [SpeakerLabel.THEIRS] (acceptable for the conservative
 *   "no fingerprint yet" bootstrap case).
 */
class SpeakerAligner(val myAliases: Set<String>) {
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
                    displayName = if (lastSpeaker == SpeakerLabel.THEIRS) null else null,
                )

            label in myAliases ->
                AlignedMessage(rawMessage = msg, speaker = SpeakerLabel.ME, displayName = null)

            else ->
                AlignedMessage(rawMessage = msg, speaker = SpeakerLabel.THEIRS, displayName = label)
        }
    }

    companion object {
        /** Fraction of messages that may be mis-aligned before a corpus is rejected. */
        const val MAX_ERROR_RATE: Double = 0.02
    }
}
