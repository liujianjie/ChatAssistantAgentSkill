package com.stylemirror.domain.candidate

/**
 * One candidate reply suggestion produced by an [LLMProvider] (or a
 * downstream candidate generator) — the unit the user picks from in the
 * realtime UI. Carries the suggested [text] plus optional metadata that the
 * candidate generator (T07) and feedback loop (T20) may attach over time.
 *
 * Intentionally minimal so T05 can ship without locking the schema. New
 * fields land with default values so downstream consumers compile without
 * change. Adding a *required* field is a breaking change on purpose — the
 * UI's candidate row is the source of truth for what a user sees.
 *
 * @property text The suggested reply, ready to copy. MUST NOT contain provider
 *   wrapping (markdown fences, role labels, etc.) — sanitisation happens at
 *   the provider boundary.
 * @property rationale Optional one-line explanation of why this candidate
 *   matches the user's style. Surfaced as a "why this?" affordance in T08.
 *   Null when the provider did not produce one (FakeLLMProvider, low-budget
 *   models, etc.).
 * @property tokens Optional token count reported by the provider. Used by
 *   the feedback loop to track per-candidate cost; null when the provider
 *   doesn't expose usage.
 */
data class Candidate(
    val text: String,
    val rationale: String? = null,
    val tokens: Int? = null,
)
