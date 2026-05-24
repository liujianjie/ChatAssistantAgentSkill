package com.stylemirror.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylemirror.app.feedback.FeedbackBuffer
import com.stylemirror.core.data.repository.FeedbackRepository
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.PartnerId
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.OcrFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.feedback.CandidateId
import com.stylemirror.domain.feedback.DiscardReason
import com.stylemirror.domain.feedback.FeedbackSignal
import com.stylemirror.domain.security.SecureKeyStore
import com.stylemirror.feature.realtime.candidate.CandidateGenerator
import com.stylemirror.feature.realtime.input.PasteEvent
import com.stylemirror.feature.realtime.input.PasteInput
import com.stylemirror.feature.realtime.input.ScreenshotInput
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

sealed class ScreenshotState {
    data object Idle : ScreenshotState()

    data object Working : ScreenshotState()

    data class Error(val message: String) : ScreenshotState()
}

@HiltViewModel
@Suppress("LongParameterList")
class MainViewModel
    @Inject
    constructor(
        private val candidateGenerator: CandidateGenerator,
        private val keyStore: SecureKeyStore,
        private val feedbackBuffer: FeedbackBuffer,
        private val feedbackRepository: FeedbackRepository,
        private val fingerprintStore: StyleFingerprintStore,
        private val screenshotInput: ScreenshotInput,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val _pasteText = MutableStateFlow("")
        val pasteText: StateFlow<String> = _pasteText.asStateFlow()

        private val _generateState = MutableStateFlow<GenerateState>(GenerateState.Idle)
        val generateState: StateFlow<GenerateState> = _generateState.asStateFlow()

        private val _apiKeyHint = MutableStateFlow("")
        val apiKeyHint: StateFlow<String> = _apiKeyHint.asStateFlow()

        private val _screenshotState = MutableStateFlow<ScreenshotState>(ScreenshotState.Idle)
        val screenshotState: StateFlow<ScreenshotState> = _screenshotState.asStateFlow()

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
            recordSignal { version ->
                FeedbackSignal.Adopt(
                    candidateId = item.id,
                    fingerprintVersion = version,
                    createdAt = Instant.now(),
                )
            }
        }

        fun discard(item: CandidateItem) {
            recordSignal { version ->
                FeedbackSignal.Discard(
                    candidateId = item.id,
                    fingerprintVersion = version,
                    createdAt = Instant.now(),
                    reason = DiscardReason.OFF_STYLE,
                )
            }
        }

        fun modify(
            item: CandidateItem,
            editedText: String,
        ) {
            if (editedText.isBlank()) return
            recordSignal { version ->
                FeedbackSignal.Modify(
                    candidateId = item.id,
                    fingerprintVersion = version,
                    createdAt = Instant.now(),
                    editedContent = editedText,
                )
            }
        }

        private fun recordSignal(build: (version: Int) -> FeedbackSignal) {
            viewModelScope.launch {
                val version =
                    fingerprintStore.findLatest()?.version ?: PLACEHOLDER_FP_VERSION
                val signal = build(version)
                // Keep in-memory buffer for current-session UX (immediate stats);
                // persist to encrypted Room so feedback survives restarts and
                // T21 incremental learner can replay it.
                feedbackBuffer.record(signal)
                feedbackRepository.record(
                    id = "fb-${Instant.now().toEpochMilli()}-${signal.candidateId.value}",
                    signal = signal,
                )
            }
        }

        fun captureScreenshot(uri: Uri) {
            _screenshotState.value = ScreenshotState.Working
            viewModelScope.launch {
                val bitmap = decodeBitmapFromUri(uri)
                if (bitmap == null) {
                    _screenshotState.value =
                        ScreenshotState.Error("无法读取图片，请重新选择")
                    return@launch
                }
                when (val result = screenshotInput.captureFrom(bitmap)) {
                    is Outcome.Ok -> {
                        // Append OCR'd text to existing paste content rather than overwrite —
                        // user may want to mix multiple screenshots before generating.
                        _pasteText.update { current ->
                            if (current.isBlank()) result.value else "$current\n${result.value}"
                        }
                        _screenshotState.value = ScreenshotState.Idle
                    }

                    is Outcome.Err ->
                        _screenshotState.value = ScreenshotState.Error(ocrErrorMessage(result.error))
                }
            }
        }

        fun dismissScreenshotError() {
            _screenshotState.value = ScreenshotState.Idle
        }

        private suspend fun decodeBitmapFromUri(uri: Uri): Bitmap? =
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
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
                            com.stylemirror.domain.error.LlmFailureReason.INVALID_RESPONSE ->
                                "模型返回无效响应${invalidResponseDetail(error.cause)}"
                        }
                    is DomainError.ImportFailure -> "输入内容无效，请检查粘贴的文本"
                    else -> "发生未知错误"
                }

            /**
             * Renders a short hint after "模型返回无效响应" so users (and we) can
             * tell apart the three INVALID_RESPONSE paths: HTTP 4xx, empty
             * choices, or local JSON parse error.
             *
             * Detection by class-name string keeps this module decoupled from
             * retrofit2 (it ships only as a transitive `implementation` dep
             * through infra-llm and is not visible at compile time here).
             */
            private fun invalidResponseDetail(cause: Throwable?): String {
                if (cause == null) return ""
                val clsName = cause::class.simpleName.orEmpty()
                if (clsName == "HttpException") {
                    // retrofit2.HttpException.message() is "HTTP 400 Bad Request"
                    val msg = cause.message.orEmpty()
                    val code = Regex("HTTP\\s+(\\d{3})").find(msg)?.groupValues?.getOrNull(1)
                    return if (code != null) "（HTTP $code）" else "（HTTP 错误）"
                }
                return "（$clsName）"
            }

            private fun ocrErrorMessage(error: DomainError): String =
                when (error) {
                    is DomainError.OcrFailure ->
                        when (error.reason) {
                            OcrFailureReason.NO_TEXT_DETECTED -> "未识别到文字，请尝试更清晰的截图"
                            OcrFailureReason.IMAGE_UNREADABLE -> "图片无法读取，请检查文件格式"
                            OcrFailureReason.PROVIDER_ERROR -> "识别服务异常，请稍后重试"
                        }
                    else -> "截图导入失败"
                }
        }
    }
