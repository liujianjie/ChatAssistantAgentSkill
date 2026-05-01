package com.stylemirror.domain.conversation

import java.time.Instant

/**
 * A single chat message inside a [ConversationContext].
 *
 * Sealed into [Mine] and [Theirs] specifically so the type system — not a
 * runtime check or convention — can guarantee that style-fingerprint
 * aggregation only sees the user's own messages. See ADR-0001.
 *
 * Aggregation entry points should declare `List<Message.Mine>`; callers narrow
 * a mixed `List<Message>` via `filterIsInstance<Message.Mine>()`.
 */
sealed interface Message {
    val id: MessageId
    val content: String
    val sentAt: Instant

    /** A message authored by the user. The only kind that may feed style profiling. */
    data class Mine(
        override val id: MessageId,
        override val content: String,
        override val sentAt: Instant,
    ) : Message

    /**
     * A message authored by someone other than the user.
     *
     * [displayName] is whatever label was visible to the user at capture time
     * (group nickname, contact alias, OCR-derived label). It MUST NOT carry
     * platform-specific identifiers — those live in the persistence layer.
     */
    data class Theirs(
        override val id: MessageId,
        override val content: String,
        override val sentAt: Instant,
        val displayName: String,
    ) : Message
}
