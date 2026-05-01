package com.stylemirror.domain.style

import com.stylemirror.domain.conversation.Message

/**
 * Type-isolated entry point for style-fingerprint aggregation.
 *
 * The signature deliberately accepts `List<Message.Mine>` rather than
 * `List<Message>`: the privacy red line (SPEC §6.3 #1) is enforced by the
 * compiler. Trying to pass a mixed list — or a `List<Message.Theirs>` — fails
 * to type-check, so future contributors cannot mix the user's voice with the
 * other party's by accident. See ADR-0001.
 */
fun interface FingerprintAggregator {
    fun aggregate(messages: List<Message.Mine>): StyleFingerprint
}
