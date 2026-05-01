package com.stylemirror.infra.llm.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs for DeepSeek's OpenAI-compatible chat-completions API.
 *
 * Kept `internal` so the rest of the codebase cannot accidentally reach for
 * vendor-shaped objects — every consumer crosses through [LLMProvider] +
 * [com.stylemirror.domain.candidate.Candidate].
 *
 * Field-name mapping favours the on-the-wire snake_case via [SerialName] so
 * Kotlin call sites stay idiomatic without scattering `@SerialName` over
 * every other field.
 */
@Serializable
internal data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val n: Int,
    val temperature: Double = 1.0,
)

@Serializable
internal data class DeepSeekMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class DeepSeekChatResponse(
    val id: String? = null,
    val choices: List<DeepSeekChoice> = emptyList(),
    val usage: DeepSeekUsage? = null,
)

@Serializable
internal data class DeepSeekChoice(
    val index: Int = 0,
    val message: DeepSeekMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class DeepSeekUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)
