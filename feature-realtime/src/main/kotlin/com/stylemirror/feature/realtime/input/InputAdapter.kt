package com.stylemirror.feature.realtime.input

import com.stylemirror.domain.conversation.ConversationContext
import kotlinx.coroutines.flow.Flow

/**
 * Boundary that turns a raw "user-supplied conversation source" (a paste, an
 * OCR'd screenshot, a system share, an accessibility-overlay capture, ...) into
 * the canonical [ConversationContext] domain value the rest of feature-realtime
 * consumes.
 *
 * Modelled as a [Flow] rather than a one-shot suspend call so the same
 * abstraction covers both bursty inputs (paste — emits once per submit) and
 * continuous capture inputs (overlay / share — may emit on every new message).
 *
 * **Contract**:
 *  - Each emission represents one "conversation snapshot" the user has chosen
 *    to act on. Implementations MUST NOT collapse two distinct user actions
 *    into a single emission.
 *  - On a recoverable parse failure the implementation should emit an empty
 *    [ConversationContext] (T07 decides whether to reject upstream); on an
 *    unimplemented capture path it should signal `DomainError.NotImplemented`.
 *    The choice is per-implementation — see the concrete classes for which
 *    convention they use.
 *  - Implementations are NOT required to produce monotonically growing
 *    contexts — speaker alignment / dedup is T11/T12's job, not the adapter's.
 */
interface InputAdapter {
    /** Stream of conversation snapshots driven by user input. Cold by convention. */
    fun receive(): Flow<ConversationContext>
}
