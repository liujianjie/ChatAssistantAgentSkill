package com.stylemirror.domain.feedback

import java.time.Instant

/**
 * Identifier for a single LLM-generated candidate reply shown to the user.
 *
 * Distinct from MessageId because candidates are pre-send: they may never
 * become messages if the user discards them.
 */
@JvmInline
value class CandidateId(val value: String) {
    init {
        require(value.isNotBlank()) { "CandidateId must not be blank" }
    }
}

/**
 * The user's reaction to a generated candidate. Every variant carries the
 * fingerprint version that produced the candidate so the incremental-learning
 * pipeline (T21) can replay feedback against the right baseline.
 *
 * Privacy red line: only the user's own decision and (for [Modify]) their own
 * edited text appear here. The other party's messages must never reach the
 * feedback ledger — that would feed back into LLM payloads for incremental
 * learning and breach SPEC §6.3 #1.
 */
sealed interface FeedbackSignal {
    val candidateId: CandidateId
    val fingerprintVersion: Int
    val createdAt: Instant

    /** User copied / sent the candidate as-is. */
    data class Adopt(
        override val candidateId: CandidateId,
        override val fingerprintVersion: Int,
        override val createdAt: Instant,
    ) : FeedbackSignal

    /**
     * User edited the candidate before sending. [editedContent] is the user's
     * own final wording — safe to feed back to incremental learning.
     */
    data class Modify(
        override val candidateId: CandidateId,
        override val fingerprintVersion: Int,
        override val createdAt: Instant,
        val editedContent: String,
    ) : FeedbackSignal {
        init {
            require(editedContent.isNotEmpty()) { "editedContent must not be empty" }
        }
    }

    /** User dismissed the candidate. Optional [reason] is a UI-side label, not free text. */
    data class Discard(
        override val candidateId: CandidateId,
        override val fingerprintVersion: Int,
        override val createdAt: Instant,
        val reason: DiscardReason,
    ) : FeedbackSignal
}

enum class DiscardReason { OFF_STYLE, OFF_TOPIC, TOO_LONG, TOO_SHORT, INAPPROPRIATE, OTHER }
