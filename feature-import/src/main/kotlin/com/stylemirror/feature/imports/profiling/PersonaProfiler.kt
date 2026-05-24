package com.stylemirror.feature.imports.profiling

import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.profiling.FingerprintJson
import com.stylemirror.core.data.repository.CorpusSampleStore
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.domain.conversation.PartnerId
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.LlmFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.style.StyleFingerprint
import com.stylemirror.feature.imports.sampling.ProfilingInput
import com.stylemirror.infra.llm.LLMProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * v2 PersonaProfiler — produces three artefacts from a [ProfilingInput]:
 *
 * 1. **6-dim StyleFingerprint** (kept from v1, downgraded to UI/隐私 use)
 * 2. **Behavior rules markdown** — 200-500 字 description of concrete habits
 *    (kǒutóuchán / responses to apologies / cold-shoulder triggers / etc.)
 * 3. **Corpus samples** — 30-80 representative messages tagged by scenario,
 *    drawn verbatim from [ProfilingInput.myMessages]
 *
 * **Privacy red lines**:
 * - Compile-time: input has no `theirMessages` field (ProfilingInput type
 *   shape). Accidentally feeding the other party's content here is a type
 *   error.
 * - Runtime: corpus sample texts are **verified to exist in input.myMessages**
 *   before being persisted (see [filterToVerbatimSamples]). LLM hallucinations
 *   that don't match are silently dropped — better undersample than smuggle a
 *   fabricated quote.
 *
 * Single LLM call produces all three artefacts to amortize prompt overhead.
 */
class PersonaProfiler(
    private val llmProvider: LLMProvider,
    private val repository: StyleFingerprintStore,
    private val corpusStore: CorpusSampleStore,
) {
    @Suppress("ReturnCount")
    suspend fun profile(
        input: ProfilingInput,
        partnerScopeId: String? = null,
        priorBehaviorRules: String? = null,
    ): Outcome<ProfileResult, DomainError> {
        if (input.myMessages.isEmpty()) {
            return Outcome.Err(
                DomainError.InsufficientProfile(
                    collectedSamples = 0,
                    required = MIN_SAMPLES_REQUIRED,
                ),
            )
        }
        val prompt = buildPrompt(input, priorBehaviorRules)
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
        val parsed =
            runCatching { parseResponse(candidate.text, partnerScopeId, input) }
                .getOrElse { e ->
                    return Outcome.Err(
                        DomainError.LlmFailure(
                            LlmFailureReason.INVALID_RESPONSE,
                            cause = e,
                        ),
                    )
                }
        val saved = persist(parsed)
        return Outcome.Ok(saved)
    }

    /** Visible for test assertions — callers must NOT pass Theirs content. */
    @Suppress("LongMethod")
    internal fun buildPrompt(
        input: ProfilingInput,
        priorBehaviorRules: String? = null,
    ): String {
        val messagesBlock =
            input.myMessages.joinToString(separator = "\n") { msg ->
                "- ${msg.content}"
            }
        val priorBlock =
            if (priorBehaviorRules.isNullOrBlank()) {
                ""
            } else {
                "\n\n            【已有的行为规则（请在此基础上演化，根据新消息调整）】\n            $priorBehaviorRules\n"
            }
        return """
            你是一个风格分析助手。以下是某位用户发送的聊天消息（仅包含该用户本人的发言）。
            请同时输出三部分内容，整体包在一个 JSON 对象里，不要添加任何 JSON 外的文本：

            {
              "fingerprint": {
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
              },
              "behavior_rules_md": "用 markdown 写 200-500 字，描述这位用户的具体说话习惯。覆盖：高频口头禅、表达不满/拒绝/道歉时的具体说法、句尾标点习惯、情绪平淡和兴奋时的差异、收到道歉/赞美的标准回复。例：'## 高频用语\\n- 「确实」「绷不住」「家人们」\\n## 拒绝时\\n- 倾向先说「行吧」+ 转移话题\\n...'",
              "corpus_samples": [
                {"text": "原话1（必须从下面用户消息列表里精确挑出，不要修改一个字）", "scenario": "日常问候"},
                {"text": "原话2", "scenario": "拒绝"}
              ]
            }

            corpus_samples 要求：
            1. 至少 30 条，最多 80 条；
            2. text 字段**必须与下面消息列表中的某一条完全一致**（一字不改），编造的会被丢弃；
            3. scenario 从下列里挑：日常问候 / 调侃 / 拒绝 / 解释 / 安慰 / 冷处理 / 道歉 / 询问 / 表态 / 其他；
            4. 覆盖至少 5 种不同 scenario，每种 scenario 至少 2 条；
            5. 优先挑能反映独特说话方式的（短的"嗯""好"少挑，挑有信息量的）；
            6. 同一句话不要挑两次。$priorBlock

            用户消息（共 ${input.totalSampled} 条）：
            $messagesBlock
            """.trimIndent()
    }

    /**
     * Parses the LLM's outer-wrapper JSON. Filters [corpus_samples] to keep
     * only those whose `text` is verbatim present in [input.myMessages] —
     * defends against fabricated quotes.
     */
    private fun parseResponse(
        text: String,
        partnerScopeId: String?,
        input: ProfilingInput,
    ): ParsedResponse {
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        require(jsonStart >= 0 && jsonEnd > jsonStart) {
            "LLM response does not contain a JSON object"
        }
        val jsonStr = text.substring(jsonStart, jsonEnd + 1)
        val wrapper = lenientJson.decodeFromString<WrapperDto>(jsonStr)

        // Inner fingerprint: round-trip through FingerprintJson by
        // re-encoding the JsonObject so we reuse FingerprintJson's lenient
        // coerce-on-unknown logic.
        val fpInnerJson = lenientJson.encodeToString(JsonObject.serializer(), wrapper.fingerprint)
        val baseFingerprint = FingerprintJson.fromJson(fpInnerJson)
        val fingerprint =
            baseFingerprint.copy(
                version = StyleFingerprint.MIN_VERSION,
                createdAt = Instant.now(),
                sampleSize = input.totalSampled,
                partnerScope = partnerScopeId?.let { PartnerId(it) },
            )

        val verbatimSamples = filterToVerbatimSamples(wrapper.corpusSamples, input)
        return ParsedResponse(
            fingerprint = fingerprint,
            behaviorRules = wrapper.behaviorRulesMd.trim(),
            corpusSamples = verbatimSamples,
            partnerScopeId = partnerScopeId,
        )
    }

    /**
     * Drops any LLM-emitted sample whose text is not literally present in the
     * user's own messages. This is the runtime privacy/truthfulness guard —
     * even though the LLM was told "don't fabricate", we don't trust it.
     */
    internal fun filterToVerbatimSamples(
        samples: List<CorpusSampleDto>,
        input: ProfilingInput,
    ): List<CorpusSampleDto> {
        val haystack = input.myMessages.map { it.content }.toHashSet()
        // Dedupe by text while preserving first occurrence's scenario tag.
        val seen = HashSet<String>()
        return samples.asSequence()
            .filter { it.text in haystack }
            .filter { seen.add(it.text) }
            .toList()
    }

    private suspend fun persist(parsed: ParsedResponse): ProfileResult {
        val version = repository.nextVersion()
        val fp = parsed.fingerprint.copy(version = version)
        val fpEntity =
            StyleFingerprintEntity(
                version = version,
                createdAtEpochMs = fp.createdAt.toEpochMilli(),
                sampleSize = fp.sampleSize,
                partnerScopeId = fp.partnerScope?.value,
                fingerprintJson = FingerprintJson.toJson(fp),
                behaviorRules = parsed.behaviorRules,
            )
        repository.insert(fpEntity)
        if (parsed.corpusSamples.isNotEmpty()) {
            corpusStore.insertAll(
                parsed.corpusSamples.map { dto ->
                    CorpusSampleEntity(
                        fingerprintVersion = version,
                        partnerScopeId = parsed.partnerScopeId,
                        text = dto.text,
                        scenario = dto.scenario,
                        createdAtEpochMs = fp.createdAt.toEpochMilli(),
                    )
                },
            )
        }
        return ProfileResult(
            fingerprint = fp,
            behaviorRules = parsed.behaviorRules,
            corpusSampleCount = parsed.corpusSamples.size,
        )
    }

    private data class ParsedResponse(
        val fingerprint: StyleFingerprint,
        val behaviorRules: String,
        val corpusSamples: List<CorpusSampleDto>,
        val partnerScopeId: String?,
    )

    @Serializable
    internal data class WrapperDto(
        val fingerprint: JsonObject,
        @SerialName("behavior_rules_md") val behaviorRulesMd: String = "",
        @SerialName("corpus_samples") val corpusSamples: List<CorpusSampleDto> = emptyList(),
    )

    @Serializable
    internal data class CorpusSampleDto(
        val text: String,
        val scenario: String,
    )

    companion object {
        const val MIN_SAMPLES_REQUIRED: Int = 10
        const val PROFILE_MAX_TOKENS: Int = 4_000

        // Lenient parser shared with FingerprintJson — accepts unknown fields,
        // recovers from coercion errors.
        private val lenientJson =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
    }
}

/** Public result of [PersonaProfiler.profile] for ViewModel consumption. */
data class ProfileResult(
    val fingerprint: StyleFingerprint,
    val behaviorRules: String,
    val corpusSampleCount: Int,
)
