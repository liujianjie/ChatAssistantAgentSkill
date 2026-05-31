package com.stylemirror.feature.overlay.service

import com.stylemirror.domain.conversation.ConversationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single in-memory channel between the accessibility service (producer) and
 * the candidate-trigger UI (consumer, T30.6). One shared flow, replay = 1
 * so a click on the bubble can grab the most recent context even if
 * Accessibility hasn't fired since the bubble opened.
 *
 * **Why a singleton object**
 *
 * The producer is a system service instantiated by Android, the consumer
 * lives inside `FloatingBubbleService` — neither has a parent we can use to
 * inject a shared instance through. A process-scoped singleton is the
 * pragmatic match. We deliberately keep it tiny so this isn't a place
 * future logic creeps into; anything stateful belongs in T30.6's controller.
 *
 * **Why not StateFlow**
 *
 * `StateFlow` deduplicates equal values. We do want every snapshot
 * delivered to whatever subscriber is listening, even if the content
 * happens to equal the previous one. `SharedFlow(replay = 1)` gives us
 * "latest available + every new emit" without the equality filter.
 *
 * **Privacy**: this object holds the user's other-party messages in
 * memory. It MUST NOT be persisted to disk anywhere (the foreground service
 * survives across app launches, but only as long as the OS keeps the
 * process alive — there is no flush-to-disk path).
 */
internal object OverlaySnapshotRepository {
    private val _latest = MutableSharedFlow<ConversationContext>(replay = 1, extraBufferCapacity = 1)

    val latest: SharedFlow<ConversationContext> = _latest.asSharedFlow()

    fun publish(context: ConversationContext) {
        _latest.tryEmit(context)
    }

    fun snapshot(): ConversationContext? = _latest.replayCache.lastOrNull()

    /** Test-only reset hook. Production code never calls this. */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun resetForTest() {
        _latest.resetReplayCache()
    }
}
