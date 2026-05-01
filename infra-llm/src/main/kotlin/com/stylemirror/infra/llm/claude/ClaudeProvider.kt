package com.stylemirror.infra.llm.claude

import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.infra.llm.LLMProvider

/**
 * Forward-compatibility stub per SPEC §1.6: the LLM layer is provider-pluggable
 * (DeepSeek default, Claude / Qwen alternates). Wiring exists so DI graphs and
 * UI provider-pickers can reference a stable type before T05's follow-up
 * implements the Anthropic Messages API.
 *
 * Calling [generateCandidates] returns [DomainError.NotImplemented]. Callers
 * that exhaustively `when` on [DomainError] already handle this branch — no
 * crashing, no silent fallback to DeepSeek.
 */
class ClaudeProvider : LLMProvider {
    override suspend fun generateCandidates(
        prompt: String,
        maxTokens: Int,
        n: Int,
    ): Outcome<List<Candidate>, DomainError> = Outcome.Err(DomainError.NotImplemented)
}
