package com.stylemirror.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylemirror.app.feedback.FeedbackBuffer
import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.PartnerId
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.feedback.CandidateId
import com.stylemirror.domain.feedback.DiscardReason
import com.stylemirror.domain.feedback.FeedbackSignal
import com.stylemirror.domain.security.SecureKeyStore
import com.stylemirror.feature.realtime.candidate.CandidateGenerator
import com.stylemirror.feature.realtime.input.PasteEvent
import com.stylemirror.feature.realtime.input.PasteInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class CandidateItem(
    val id: CandidateId,
    val candidate: Candidate,
)

sealed class GenerateState {
    data object Idle : GenerateState()

    data object Generating : GenerateState()

    data class Ready(val items: List<CandidateItem>) : GenerateState()

    data class Error(val message: String) : GenerateState()
}

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val candidateGenerator: CandidateGenerator,
        private val keyStore: SecureKeyStore,
        private val feedbackBuffer: FeedbackBuffer,
    ) : ViewModel() {
        private val _pasteText = MutableStateFlow("")
        val pasteText: StateFlow<String> = _pasteText.asStateFlow()

        private val _generateState = MutableStateFlow<GenerateState>(GenerateState.Idle)
        val generateState: StateFlow<GenerateState> = _generateState.asStateFlow()

        private val _apiKeyHint = MutableStateFlow("")
        val apiKeyHint: StateFlow<String> = _apiKeyHint.asStateFlow()

        init {
            viewModelScope.launch { refreshApiKeyHint() }
        }

        fun onPasteTextChange(text: String) {
            _pasteText.value = text
            if (_generateState.value is GenerateState.Ready || _generateState.value is GenerateState.Error) {
                _generateState.value = GenerateState.Idle
            }
        }

        fun generate() {
            val raw = _pasteText.value.trim()
            if (raw.isEmpty()) return
            _generateState.value = GenerateState.Generating

            viewModelScope.launch {
                val input = PasteInput(flowOf(PasteEvent(rawText = raw, partnerId = PartnerId("current"))))
                val context: ConversationContext = input.receive().first()
                when (val result = candidateGenerator.generate(context)) {
                    is Outcome.Ok -> {
                        val items =
                            result.value.mapIndexed { idx, c ->
                                CandidateItem(
                                    id = CandidateId("cand-${Instant.now().toEpochMilli()}-$idx"),
                                    candidate = c,
                                )
                            }
                        _generateState.value = GenerateState.Ready(items)
                    }
                    is Outcome.Err -> _generateState.value = GenerateState.Error(domainErrorMessage(result.error))
                }
            }
        }

        fun adopt(item: CandidateItem) {
            feedbackBuffer.record(
                FeedbackSignal.Adopt(
                    candidateId = item.id,
                    fingerprintVersion = PLACEHOLDER_FP_VERSION,
                    createdAt = Instant.now(),
                ),
            )
        }

        fun discard(item: CandidateItem) {
            feedbackBuffer.record(
                FeedbackSignal.Discard(
                    candidateId = item.id,
                    fingerprintVersion = PLACEHOLDER_FP_VERSION,
                    createdAt = Instant.now(),
                    reason = DiscardReason.OFF_STYLE,
                ),
            )
        }

        fun modify(
            item: CandidateItem,
            editedText: String,
        ) {
            if (editedText.isBlank()) return
            feedbackBuffer.record(
                FeedbackSignal.Modify(
                    candidateId = item.id,
                    fingerprintVersion = PLACEHOLDER_FP_VERSION,
                    createdAt = Instant.now(),
                    editedContent = editedText,
                ),
            )
        }

        fun saveApiKey(key: String) {
            if (key.isBlank()) return
            viewModelScope.launch {
                keyStore.put(name = API_KEY_STORE_NAME, value = key.trim())
                refreshApiKeyHint()
            }
        }

        fun clearApiKey() {
            viewModelScope.launch {
                keyStore.remove(name = API_KEY_STORE_NAME)
                refreshApiKeyHint()
            }
        }

        private suspend fun refreshApiKeyHint() {
            val raw = keyStore.get(API_KEY_STORE_NAME)
            _apiKeyHint.update {
                if (raw.isNullOrBlank()) "" else maskKey(raw)
            }
        }

        companion object {
            const val API_KEY_STORE_NAME: String = "llm.deepseek.api_key"
            private const val PLACEHOLDER_FP_VERSION: Int = 1

            private fun maskKey(key: String): String {
                if (key.length <= 8) return "****"
                return "${key.take(4)}…${key.takeLast(4)}"
            }

            private fun domainErrorMessage(error: DomainError): String =
                when (error) {
                    is DomainError.LlmFailure ->
                        when (error.reason) {
                            com.stylemirror.domain.error.LlmFailureReason.KEY_MISSING -> "请先在设置中填写 DeepSeek API Key"
                            com.stylemirror.domain.error.LlmFailureReason.TIMEOUT -> "网络超时，请重试"
                            com.stylemirror.domain.error.LlmFailureReason.RATE_LIMITED -> "请求过于频繁，请稍后再试"
                            com.stylemirror.domain.error.LlmFailureReason.AUTH -> "API Key 无效，请在设置中更新"
                            com.stylemirror.domain.error.LlmFailureReason.SERVER_ERROR -> "服务器错误，请稍后再试"
                            com.stylemirror.domain.error.LlmFailureReason.INVALID_RESPONSE -> "模型返回无效响应"
                        }
                    is DomainError.ImportFailure -> "输入内容无效，请检查粘贴的文本"
                    else -> "发生未知错误"
                }
        }
    }
