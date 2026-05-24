package com.stylemirror.app.feedback

import com.stylemirror.domain.feedback.FeedbackSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory staging area for feedback signals collected during a session.
 *
 * This is the M1 placeholder for T20's [RoomFeedbackRepository]. The interface
 * contract is intentionally minimal so T20 can swap the backing store without
 * touching call sites.
 */
@Singleton
class FeedbackBuffer
    @Inject
    constructor() {
        private val _signals = MutableStateFlow<List<FeedbackSignal>>(emptyList())
        val signals: StateFlow<List<FeedbackSignal>> = _signals.asStateFlow()

        fun record(signal: FeedbackSignal) {
            _signals.update { it + signal }
        }

        fun clear() {
            _signals.value = emptyList()
        }
    }
