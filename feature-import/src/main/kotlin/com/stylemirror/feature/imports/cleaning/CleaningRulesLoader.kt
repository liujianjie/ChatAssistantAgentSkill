package com.stylemirror.feature.imports.cleaning

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Loads [CleaningRules] from a YAML stream.
 *
 * Expected YAML schema:
 * ```yaml
 * merge_adjacent_same_speaker: true
 * normalize_emoji_whitespace: true
 * filter_patterns:
 *   - pattern: "[转账]"
 *     type: EXACT_CONTENT
 *   - pattern: "^\\[.*\\]$"
 *     type: REGEX
 * ```
 *
 * Unknown keys are silently ignored; missing keys get their defaults from
 * [CleaningRules]. This makes YAML hot-replacement in tests safe — tests can
 * supply partial YAML without risking NPE on unspecified fields.
 */
object CleaningRulesLoader {
    @Suppress("UNCHECKED_CAST")
    fun load(yaml: String): CleaningRules {
        val options = LoaderOptions()
        val y = Yaml(SafeConstructor(options))
        val map = y.load<Map<String, Any>>(yaml) ?: emptyMap()

        val merge = map["merge_adjacent_same_speaker"] as? Boolean ?: true
        val normalize = map["normalize_emoji_whitespace"] as? Boolean ?: true

        val rawPatterns = map["filter_patterns"] as? List<Map<String, Any>> ?: emptyList()
        val patterns =
            rawPatterns.mapNotNull { entry ->
                val p = entry["pattern"] as? String ?: return@mapNotNull null
                val typeStr = entry["type"] as? String ?: "EXACT_CONTENT"
                val type =
                    runCatching<CleaningRules.PatternType> {
                        CleaningRules.PatternType.valueOf(typeStr)
                    }.getOrElse { CleaningRules.PatternType.EXACT_CONTENT }
                CleaningRules.FilterPattern(pattern = p, type = type)
            }

        return CleaningRules(
            mergeAdjacentSameSpeaker = merge,
            filterPatterns = patterns,
            normalizeEmojiWhitespace = normalize,
        )
    }

    fun loadDefault(): CleaningRules =
        CleaningRulesLoader::class.java
            .getResourceAsStream("/cleaning_rules/default.yaml")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?.let { load(it) }
            ?: CleaningRules()
}
