package com.stylemirror.infra.llm

import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome

/**
 * Provider-agnostic LLM contract used by the candidate generator (T07).
 *
 * Implementations: [FakeLLMProvider] for CI / unit tests, DeepSeekProvider
 * for production (T05-S2), Claude / Qwen stubs (T05-S3) for future swap-in.
 * The contract MUST NOT leak vendor-specific fields — anything provider-shaped
 * (model name, system prompt structure, function-calling schema) belongs in
 * the implementation, not on this interface.
 *
 * Error channel widens to [DomainError] (rather than the narrower
 * `DomainError.LlmFailure`) so unimplemented stubs can return
 * [DomainError.NotImplemented] without forcing every caller to handle a
 * synthetic LlmFailureReason. Consumers should `when` over the sealed class
 * to stay exhaustive.
 *
 * @param prompt The full prompt as a single string. Caller bakes in any
 *   system-style framing; providers send it as a user-role message.
 * @param maxTokens Soft upper bound on each candidate's length.
 * @param n Number of candidates to return. Providers SHOULD honour this; if
 *   the underlying API can't produce [n] in one call, implementations MAY
 *   fan out internally — never less than 1.
 */
interface LLMProvider {
    suspend fun generateCandidates(
        prompt: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        n: Int = DEFAULT_N,
    ): Outcome<List<Candidate>, DomainError>

    companion object {
        const val DEFAULT_MAX_TOKENS: Int = 256
        const val DEFAULT_N: Int = 3
    }
}
