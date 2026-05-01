package com.stylemirror.feature.realtime.input

import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.error.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Placeholder for the Android system-share-sheet entry point. Becomes a real
 * implementation alongside the `ACTION_SEND` receiver in a later task; the
 * interface seat exists today so navigation routing can be sketched without a
 * forward dependency on platform glue.
 *
 * Calling [receive] and collecting the resulting flow will surface
 * [DomainError.NotImplemented] via [DomainErrorException].
 */
class ShareSheetInput : InputAdapter {
    override fun receive(): Flow<ConversationContext> =
        flow {
            throw DomainErrorException(DomainError.NotImplemented)
        }
}
