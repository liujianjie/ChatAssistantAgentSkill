package com.stylemirror.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylemirror.app.MainViewModel
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.LlmFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.security.SecureKeyStore
import com.stylemirror.domain.style.StyleFingerprint
import com.stylemirror.feature.imports.alignment.SpeakerAligner
import com.stylemirror.feature.imports.cleaning.MessageCleaner
import com.stylemirror.feature.imports.profiling.PersonaProfiler
import com.stylemirror.feature.imports.sampling.MessageSampler
import com.stylemirror.feature.imports.source.PlainTextImportSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Suspendable text-source closure passed to [OnboardingViewModel.loadFromTextSource].
 *
 * Production wiring builds the closure inline in MainActivity around a SAF Uri
 * + ContentResolver. Tests pass a literal string. The ViewModel never sees
 * Android Uri or Context.
 */
fun interface TextSource {
    suspend fun read(): String
}

/**
 * Drives the onboarding pipeline:
 *   PlainTextImportSource → MessageCleaner → SpeakerAligner →
 *   MessageSampler → PersonaProfiler → StyleFingerprintRepository.
 *
 * State is exposed as a small, testable [OnboardingState] sealed hierarchy so
 * the UI can render either a step indicator (importing/profiling) or a
 * fingerprint summary (Ready) without leaking pipeline internals.
 *
 * Privacy red line: only the user's own messages reach [PersonaProfiler] —
 * enforced at compile time by `ProfilingInput.myMessages` (no Theirs field).
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val personaProfiler: PersonaProfiler,
        private val keyStore: SecureKeyStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow<OnboardingState>(OnboardingState.AskAliases)
        val state: StateFlow<OnboardingState> = _state.asStateFlow()

        private val _aliases = MutableStateFlow("")
        val aliases: StateFlow<String> = _aliases.asStateFlow()

        private val _pasteText = MutableStateFlow("")
        val pasteText: StateFlow<String> = _pasteText.asStateFlow()

        fun onAliasesChange(text: String) {
            _aliases.value = text
        }

        fun onPasteChange(text: String) {
            _pasteText.value = text
        }

        fun confirmAliases() {
            val parsed = parseAliases(_aliases.value)
            if (parsed.isEmpty()) return
            _state.value = OnboardingState.AskCorpus
        }

        fun backToAliases() {
            _state.value = OnboardingState.AskAliases
        }

        fun runProfiling() {
            val aliasSet = parseAliases(_aliases.value)
            if (aliasSet.isEmpty()) {
                _state.value = OnboardingState.Error("请先填写至少一个昵称别名")
                return
            }
            val text = _pasteText.value
            if (text.isBlank()) {
                _state.value = OnboardingState.Error("请粘贴聊天记录后再继续")
                return
            }

            viewModelScope.launch {
                if (!ensureApiKeyPresent()) return@launch

                _state.value = OnboardingState.Working(Stage.IMPORTING)
                val raw =
                    runCatching {
                        PlainTextImportSource(text).stream().toList()
                    }.getOrElse { e ->
                        _state.value = OnboardingState.Error("导入失败：${e.message ?: "未知错误"}")
                        return@launch
                    }
                if (raw.isEmpty()) {
                    _state.value = OnboardingState.Error("聊天记录为空，请检查粘贴内容")
                    return@launch
                }

                _state.value = OnboardingState.Working(Stage.CLEANING)
                val cleaned = MessageCleaner().clean(raw)

                _state.value = OnboardingState.Working(Stage.ALIGNING)
                val aligned = SpeakerAligner(myAliases = aliasSet).align(cleaned)

                _state.value = OnboardingState.Working(Stage.SAMPLING)
                val sampled = MessageSampler().sample(aligned)
                if (sampled.totalSampled < PersonaProfiler.MIN_SAMPLES_REQUIRED) {
                    _state.value =
                        OnboardingState.Error(
                            "你（${aliasSet.joinToString("/")}）的发言数 ${sampled.totalSampled} 条，" +
                                "至少需要 ${PersonaProfiler.MIN_SAMPLES_REQUIRED} 条。请检查别名或导入更多对话。",
                        )
                    return@launch
                }

                _state.value = OnboardingState.Working(Stage.PROFILING)
                when (val result = personaProfiler.profile(sampled)) {
                    is Outcome.Ok ->
                        _state.value =
                            OnboardingState.Ready(
                                fingerprint = result.value,
                                summary = StyleFingerprintSummary.of(result.value, sampled.totalSampled),
                            )

                    is Outcome.Err ->
                        _state.value =
                            OnboardingState.Error(domainErrorMessage(result.error, sampled.totalSampled))
                }
            }
        }

        /**
         * Pre-flight check before kicking off the profiling pipeline.
         *
         * Self-use feedback: a missing API Key was masked by an upstream
         * alignment bug that caused InsufficientProfile to fire first; we now
         * surface the missing-key state explicitly so users don't run the full
         * pipeline only to fail at the LLM call.
         *
         * @return true if a non-blank key is present, false (and sets Error
         * state) otherwise.
         */
        private suspend fun ensureApiKeyPresent(): Boolean {
            val apiKey = keyStore.get(name = MainViewModel.API_KEY_STORE_NAME)
            if (apiKey.isNullOrBlank()) {
                _state.value =
                    OnboardingState.Error(
                        "尚未设置 DeepSeek API Key。请先返回主页 → 设置 → 填入 Key 后再开始画像。",
                    )
                return false
            }
            return true
        }

        fun resetToAskAliases() {
            _state.value = OnboardingState.AskAliases
        }

        /**
         * Reads text from a caller-supplied [TextSource] (production wraps a SAF
         * Uri + ContentResolver) and loads it into the paste field. Errors
         * thrown by the source surface as [OnboardingState.Error].
         */
        fun loadFromTextSource(source: TextSource) {
            viewModelScope.launch {
                runCatching { source.read() }
                    .onSuccess { text ->
                        _pasteText.value = text
                        if (_state.value is OnboardingState.Error) {
                            _state.value = OnboardingState.AskCorpus
                        }
                    }
                    .onFailure { e ->
                        _state.value = OnboardingState.Error("文件导入失败：${e.message ?: "未知错误"}")
                    }
            }
        }

        companion object {
            const val MAX_FILE_BYTES: Int = 50 * 1024 * 1024 // 50 MB

            internal fun parseAliases(raw: String): Set<String> =
                raw
                    .split(',', '，', '\n', ' ', '、', ';', '；')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()

            internal fun domainErrorMessage(
                error: DomainError,
                sampleCount: Int,
            ): String =
                when (error) {
                    is DomainError.LlmFailure ->
                        when (error.reason) {
                            LlmFailureReason.KEY_MISSING -> "请先在设置中填写 DeepSeek API Key"
                            LlmFailureReason.TIMEOUT -> "网络超时，请稍后重试"
                            LlmFailureReason.RATE_LIMITED -> "请求过于频繁，请稍后再试"
                            LlmFailureReason.AUTH -> "API Key 无效，请在设置中更新"
                            LlmFailureReason.SERVER_ERROR -> "模型服务异常，请稍后重试"
                            LlmFailureReason.INVALID_RESPONSE -> "模型返回的画像无法解析，请重试"
                        }

                    is DomainError.InsufficientProfile ->
                        "样本不足：当前 $sampleCount 条，至少需要 ${error.required} 条。"

                    is DomainError.ImportFailure -> "导入解析失败，请检查内容格式"
                    else -> "画像生成失败，请重试"
                }
        }
    }

enum class Stage {
    IMPORTING,
    CLEANING,
    ALIGNING,
    SAMPLING,
    PROFILING,
    ;

    val label: String
        get() =
            when (this) {
                IMPORTING -> "正在解析聊天记录…"
                CLEANING -> "正在清洗噪音…"
                ALIGNING -> "正在识别说话人…"
                SAMPLING -> "正在采样消息…"
                PROFILING -> "正在生成画像（调用大模型）…"
            }
}

sealed class OnboardingState {
    data object AskAliases : OnboardingState()

    data object AskCorpus : OnboardingState()

    data class Working(val stage: Stage) : OnboardingState()

    data class Ready(
        val fingerprint: StyleFingerprint,
        val summary: StyleFingerprintSummary,
    ) : OnboardingState()

    data class Error(val message: String) : OnboardingState()
}
