package com.stylemirror.app.di

import javax.inject.Qualifier

/**
 * Qualifies the LLMProvider used for the realtime candidate-generation path
 * (tight 8s timeout, candidate.LLMProvider in DeepSeekProvider.create).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CandidateLlm

/**
 * Qualifies the LLMProvider used for one-shot profiling/onboarding/incremental-
 * learning paths (90s timeout, larger payloads). PersonaProfiler and
 * IncrementalLearner depend on this qualifier.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProfilingLlm
