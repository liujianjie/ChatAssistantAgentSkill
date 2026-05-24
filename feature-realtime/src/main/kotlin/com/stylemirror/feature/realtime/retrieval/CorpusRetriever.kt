package com.stylemirror.feature.realtime.retrieval

import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import com.stylemirror.core.data.repository.CorpusSampleStore

/**
 * Picks a small set of corpus samples to inject as few-shot examples in the
 * candidate-generation prompt (画像 v2 / ADR-0005).
 *
 * Algorithm (intentionally simple — first version):
 *   1. Guess the conversation scenario from regex over the other party's
 *      recent messages (apology / greeting / question / refusal / other).
 *   2. Score every active sample by:
 *        scenarioBonus (3.0 if scenario == guess) + bigramOverlap(text, query)
 *   3. Take top-N globally by score.
 *
 * No BM25, no vectors — at 30–80 samples the simple bigram-overlap is plenty.
 * If recall ever becomes the bottleneck we'll upgrade (see ADR-0005 §"Rejected").
 *
 * Zero corpus → empty result; CandidateGenerator handles the fallback to v1
 * prompt shape.
 */
class CorpusRetriever(
    private val corpusStore: CorpusSampleStore,
    private val topN: Int = DEFAULT_TOP_N,
) {
    /**
     * @param fingerprintVersion which fingerprint version's corpus to query
     * @param theirRecentMessages the other party's most recent messages — drives
     *   both the scenario guess and the overlap scoring
     */
    suspend fun retrieve(
        fingerprintVersion: Int,
        theirRecentMessages: List<String>,
    ): List<CorpusSampleEntity> {
        val all = corpusStore.findActiveByVersion(fingerprintVersion)
        if (all.isEmpty()) return emptyList()
        val query = theirRecentMessages.joinToString(separator = " ")
        val scenario = guessScenario(query)
        val queryTokens = bigramTokens(query)
        return all.asSequence()
            .map { sample -> sample to scoreSample(sample, queryTokens, scenario) }
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
            .toList()
    }

    internal fun guessScenario(text: String): String? =
        when {
            APOLOGY_REGEX.containsMatchIn(text) -> "安慰"
            GREETING_REGEX.containsMatchIn(text) -> "日常问候"
            REFUSAL_REGEX.containsMatchIn(text) -> "拒绝"
            QUESTION_REGEX.containsMatchIn(text) -> "解释"
            COMFORT_REGEX.containsMatchIn(text) -> "安慰"
            JOKE_REGEX.containsMatchIn(text) -> "调侃"
            else -> null
        }

    internal fun scoreSample(
        sample: CorpusSampleEntity,
        queryTokens: Set<String>,
        scenarioGuess: String?,
    ): Double {
        val scenarioBonus = if (scenarioGuess != null && scenarioGuess == sample.scenario) SCENARIO_BONUS else 0.0
        val sampleTokens = bigramTokens(sample.text)
        val overlap = sampleTokens.intersect(queryTokens).size
        return scenarioBonus + overlap.toDouble()
    }

    /**
     * Naive Chinese bigram tokeniser: every consecutive pair of non-whitespace
     * characters becomes a token. Latin runs are tokenised as words too.
     * Good enough for 30–80-sample corpora.
     */
    internal fun bigramTokens(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val out = HashSet<String>()
        val cleaned = text.filterNot { it.isWhitespace() }
        for (i in 0 until cleaned.length - 1) {
            out.add(cleaned.substring(i, i + 2))
        }
        // Also include alphanumeric word tokens.
        WORD_REGEX.findAll(text).forEach { out.add(it.value.lowercase()) }
        return out
    }

    companion object {
        const val DEFAULT_TOP_N: Int = 5
        private const val SCENARIO_BONUS: Double = 3.0

        private val APOLOGY_REGEX = Regex("对不起|抱歉|不好意思|sorry", RegexOption.IGNORE_CASE)
        private val GREETING_REGEX = Regex("在吗|你好|早安|早上好|晚安|hi|hello", RegexOption.IGNORE_CASE)
        private val REFUSAL_REGEX = Regex("不去|算了|不行|拒绝|没空")
        private val QUESTION_REGEX = Regex("为什么|怎么|怎样|呢[？?]|[？?]")
        private val COMFORT_REGEX = Regex("难过|不开心|烦|郁闷|哭")
        private val JOKE_REGEX = Regex("哈哈|笑死|绷不住|草|不是吧")
        private val WORD_REGEX = Regex("[A-Za-z0-9]+")
    }
}
