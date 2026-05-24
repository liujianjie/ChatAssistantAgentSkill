package com.stylemirror.feature.imports.profiling

import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.profiling.FingerprintJson
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.domain.conversation.PartnerId
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.LlmFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.style.StyleFingerprint
import com.stylemirror.feature.imports.sampling.ProfilingInput
import com.stylemirror.infra.llm.LLMProvider
import java.time.Instant

/**
 * Extracts a [StyleFingerprint] from a [ProfilingInput] by calling an LLM
 * and parsing the structured JSON response.
 *
 * **Privacy red line (enforced at compile time)**:
 * [ProfilingInput.myMessages] contains **only the user's own messages**.
 * There is no `theirMessages` field in [ProfilingInput] — accidentally
 * including the other party's content in the profiling payload is a
 * compile-time error, not a runtime check.
 *
 * @param llmProvider Provider for the profiling LLM call. One call per
 *   [profile] invocation (n=1, higher maxTokens than candidate generation).
 * @param repository Persists the generated fingerprint and assigns a version.
 */
class PersonaProfiler(
    private val llmProvider: LLMProvider,
    private val repository: StyleFingerprintStore,
) {
    @Suppress("ReturnCount")
    suspend fun profile(
        input: ProfilingInput,
        partnerScopeId: String? = null,
    ): Outcome<StyleFingerprint, DomainError> {
        if (input.myMessages.isEmpty()) {
            return Outcome.Err(
                DomainError.InsufficientProfile(
                    collectedSamples = 0,
                    required = MIN_SAMPLES_REQUIRED,
                ),
            )
        }
        val prompt = buildPrompt(input)
        val llmResult =
            llmProvider.generateCandidates(
                prompt = prompt,
                maxTokens = PROFILE_MAX_TOKENS,
                n = 1,
            )
        val candidate =
            when (llmResult) {
                is Outcome.Ok ->
                    llmResult.value.firstOrNull()
                        ?: return Outcome.Err(DomainError.LlmFailure(LlmFailureReason.INVALID_RESPONSE))
                is Outcome.Err -> return llmResult
            }
        val fingerprint =
            runCatching { parseResponse(candidate.text, partnerScopeId) }
                .getOrElse { e ->
                    return Outcome.Err(
                        DomainError.LlmFailure(
                            LlmFailureReason.INVALID_RESPONSE,
                            cause = e,
                        ),
                    )
                }
        persistFingerprint(fingerprint)
        return Outcome.Ok(fingerprint)
    }

    /** Visible for test assertions — callers must NOT pass Theirs content. */
    internal fun buildPrompt(input: ProfilingInput): String {
        val messages =
            input.myMessages.joinToString(separator = "\n") { msg ->
                "- ${msg.content}"
            }
        return """
            你是一个风格分析助手。以下是某位用户发送的聊天消息（仅包含该用户本人的发言）。
            请分析其说话风格，并仅以如下 JSON 格式输出，不要添加任何其他内容：

            {
              "linguistic": {
                "formality": "CASUAL|NEUTRAL|FORMAL",
                "vocabularyComplexity": 0.0-1.0,
                "sentencePattern": "SHORT_FRAGMENTED|MIXED|LONG_STRUCTURED",
                "signaturePhrases": ["phrase1", "phrase2"]
              },
              "emotional": {
                "emojiDensity": 每100字符的emoji数量,
                "exclamationFrequency": 0.0-1.0,
                "tone": "RESERVED|BALANCED|EXPRESSIVE",
                "preferredEmojis": ["emoji1", "emoji2"]
              },
              "humor": {
                "frequency": 0.0-1.0,
                "types": ["NONE|SELF_DEPRECATING|WORDPLAY|OBSERVATIONAL|ABSURDIST|DEADPAN"]
              },
              "avoidance": {
                "topicsAvoided": ["topic1"],
                "hedgingFrequency": 0.0-1.0,
                "deflectionStrategy": "NONE|SILENT|REDIRECT|JOKE"
              },
              "pacing": {
                "avgMessageLength": 平均消息字符数,
                "avgMessagesPerTurn": 平均每轮消息数,
                "responseDelay": "IMMEDIATE|MINUTES|HOURS|MIXED"
              },
              "sensitive": {
                "directness": "DIRECT|INDIRECT|EVASIVE",
                "approach": "ANALYTICAL|EMPATHETIC|PRAGMATIC"
              }
            }

            用户消息（共 ${input.totalSampled} 条）：
            $messages
            """.trimIndent()
    }

    private fun parseResponse(
        text: String,
        partnerScopeId: String?,
    ): StyleFingerprint {
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        require(jsonStart >= 0 && jsonEnd > jsonStart) {
            "LLM response does not contain a JSON object"
        }
        val jsonStr = text.substring(jsonStart, jsonEnd + 1)
        val base = FingerprintJson.fromJson(jsonStr)
        return base.copy(
            // version will be overwritten in persistFingerprint with DB-assigned version
            version = StyleFingerprint.MIN_VERSION,
            createdAt = Instant.now(),
            partnerScope =
                partnerScopeId?.let {
                    PartnerId(it)
                },
        )
    }

    private suspend fun persistFingerprint(fp: StyleFingerprint) {
        val version = repository.nextVersion()
        val entity =
            StyleFingerprintEntity(
                version = version,
                createdAtEpochMs = fp.createdAt.toEpochMilli(),
                sampleSize = fp.sampleSize,
                partnerScopeId = fp.partnerScope?.value,
                fingerprintJson = FingerprintJson.toJson(fp.copy(version = version)),
            )
        repository.insert(entity)
    }

    companion object {
        const val MIN_SAMPLES_REQUIRED: Int = 10
        const val PROFILE_MAX_TOKENS: Int = 2_000
    }
}
