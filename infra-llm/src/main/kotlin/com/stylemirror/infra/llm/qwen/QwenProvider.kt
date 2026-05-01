package com.stylemirror.infra.llm.qwen

import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.infra.llm.LLMProvider

/**
 * Forward-compatibility stub per SPEC §1.6. See [com.stylemirror.infra.llm.claude.ClaudeProvider]
 * for the rationale — same shape, different vendor (DashScope-compatible).
 */
class QwenProvider : LLMProvider {
    override suspend fun generateCandidates(
        prompt: String,
        maxTokens: Int,
        n: Int,
    ): Outcome<List<Candidate>, DomainError> = Outcome.Err(DomainError.NotImplemented)
}
