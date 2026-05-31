package com.stylemirror.feature.overlay.candidate

import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.LlmFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.feature.overlay.service.OverlaySnapshotRepository
import com.stylemirror.feature.realtime.candidate.CandidateGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State machine driving the floating-bubble candidate flow (T30.6).
 *
 * One instance per service lifecycle — created when [com.stylemirror.feature.overlay.service.FloatingBubbleService]
 * starts, cancelled when it stops. Reads the latest snapshot from
 * [OverlaySnapshotRepository], delegates to [CandidateGenerator] (P0
 * pipeline reused intact — including its privacy redaction layer), and
 * exposes a [StateFlow] for the bubble UI to render.
 *
 * **State transitions**
 *
 *   Idle ──trigger()──▶ Loading ──success──▶ Ready
 *                    └─snapshot empty─▶ Empty
 *                    └─error──────────▶ Error
 *   {Ready, Empty, Error} ──dismiss()──▶ Idle
 *
 * Multiple in-flight triggers are coalesced — calling trigger() while
 * Loading is a no-op (the previous job stays in flight). This avoids
 * fan-out when the user double-taps the bubble.
 */
class OverlayCandidateController(
    private val candidateGenerator: CandidateGenerator,
    private val scope: CoroutineScope,
    private val snapshotProvider: () -> com.stylemirror.domain.conversation.ConversationContext? = {
        OverlaySnapshotRepository.snapshot()
    },
) {
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var inflight: Job? = null

    fun trigger() {
        if (_state.value is UiState.Loading) return
        val context = snapshotProvider()
        if (context == null || context.theirMessages.isEmpty()) {
            _state.value = UiState.Empty
            return
        }
        _state.value = UiState.Loading
        inflight =
            scope.launch {
                when (val result = candidateGenerator.generate(context)) {
                    is Outcome.Ok -> _state.value = UiState.Ready(result.value)
                    is Outcome.Err -> _state.value = UiState.Error(formatError(result.error))
                }
            }
    }

    fun dismiss() {
        inflight?.cancel()
        inflight = null
        _state.value = UiState.Idle
    }

    private fun formatError(error: DomainError): String =
        when (error) {
            is DomainError.LlmFailure ->
                when (error.reason) {
                    LlmFailureReason.TIMEOUT -> "模型调用超时"
                    LlmFailureReason.RATE_LIMITED -> "调用过频，稍候再试"
                    LlmFailureReason.AUTH, LlmFailureReason.KEY_MISSING -> "API Key 无效或未配置"
                    LlmFailureReason.SERVER_ERROR -> "模型服务异常"
                    LlmFailureReason.INVALID_RESPONSE -> "模型返回无效响应"
                }
            is DomainError.ImportFailure -> "对话内容不足"
            is DomainError.InsufficientProfile -> "画像还不充分，先补几次反馈"
            is DomainError.QuotaExceeded -> "已达调用配额上限"
            DomainError.NotImplemented -> "该功能尚未实装"
            is DomainError.OcrFailure -> "OCR 出错（非悬浮窗路径）"
        }

    sealed interface UiState {
        data object Idle : UiState

        data object Loading : UiState

        /** Snapshot was empty — bubble was clicked before any Soul content was captured. */
        data object Empty : UiState

        data class Ready(val candidates: List<Candidate>) : UiState

        data class Error(val message: String) : UiState
    }
}
