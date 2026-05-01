package com.stylemirror.infra.llm

import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome

/**
 * Test / CI implementation of [LLMProvider]. Returns deterministic candidates
 * with no network calls, so unit tests and the candidate-generator pipeline
 * can be exercised without a live API key.
 *
 * The default [responder] produces [n] generic placeholder candidates. Tests
 * inject a custom responder to stage success / error / partial cases —
 * provider-specific failure modes (rate limiting, timeout, key missing) are
 * exercised against [com.stylemirror.infra.llm.deepseek.DeepSeekProvider]
 * with MockWebServer instead.
 *
 * The responder receives the original prompt + n so tests can assert that
 * the candidate generator is calling through with the inputs it expects.
 */
class FakeLLMProvider(
    private val responder: (prompt: String, n: Int) -> Outcome<List<Candidate>, DomainError> = ::defaultResponder,
) : LLMProvider {
    override suspend fun generateCandidates(
        prompt: String,
        maxTokens: Int,
        n: Int,
    ): Outcome<List<Candidate>, DomainError> = responder(prompt, n)

    private companion object {
        fun defaultResponder(
            @Suppress("UNUSED_PARAMETER") prompt: String,
            n: Int,
        ): Outcome<List<Candidate>, DomainError> =
            Outcome.Ok(
                List(n.coerceAtLeast(1)) { i ->
                    Candidate(
                        text = "fake candidate ${i + 1}",
                        rationale = "FakeLLMProvider deterministic placeholder",
                    )
                },
            )
    }
}
