package com.stylemirror.feature.realtime.input

import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.error.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Placeholder for the accessibility-overlay live-capture path. Concrete
 * implementation arrives with T18; this class is here so the adapter contract
 * can be referenced from DI graphs and feature toggles ahead of time.
 *
 * Calling [receive] and collecting the resulting flow will surface
 * [DomainError.NotImplemented] via [DomainErrorException].
 */
class OverlayInput : InputAdapter {
    override fun receive(): Flow<ConversationContext> =
        flow {
            throw DomainErrorException(DomainError.NotImplemented)
        }
}
