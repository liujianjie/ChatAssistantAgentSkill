package com.stylemirror.domain.conversation

/**
 * A snapshot of a 1:1 or group conversation viewed from the user's
 * perspective: who they're talking to, and the chronologically-ordered messages
 * in scope. Treated as an immutable value — input adapters and importers each
 * produce one per delivery.
 *
 * Empty contexts are allowed (e.g. an empty conversation kicking off
 * onboarding); downstream stages decide whether they require a minimum size.
 */
data class ConversationContext(
    val partnerId: PartnerId,
    val messages: List<Message>,
) {
    /** Convenience accessor — does not mutate; returns a typed sublist. */
    val myMessages: List<Message.Mine>
        get() = messages.filterIsInstance<Message.Mine>()

    /** Convenience accessor — counterpart to [myMessages]. */
    val theirMessages: List<Message.Theirs>
        get() = messages.filterIsInstance<Message.Theirs>()
}
