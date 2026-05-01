package com.stylemirror.feature.realtime.input

import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.error.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Placeholder for the screenshot-OCR capture path scheduled for T17. Kept here
 * so the [InputAdapter] interface position is locked in before its dependency
 * on infra-ocr lands — switching the concrete implementation later won't
 * ripple into call sites.
 *
 * Calling [receive] and collecting the resulting flow will surface
 * [DomainError.NotImplemented] via [DomainErrorException].
 */
class ScreenshotInput : InputAdapter {
    override fun receive(): Flow<ConversationContext> =
        flow {
            throw DomainErrorException(DomainError.NotImplemented)
        }
}
