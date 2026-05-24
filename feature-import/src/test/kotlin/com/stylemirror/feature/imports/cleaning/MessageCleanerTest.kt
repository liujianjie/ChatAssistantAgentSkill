package com.stylemirror.feature.imports.cleaning

import com.stylemirror.feature.imports.source.RawMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private fun raw(
    content: String,
    speaker: String? = "张三",
    idx: Int = 0,
) = RawMessage(rawSpeakerLabel = speaker, content = content, timestampHint = null, sourceIndex = idx)

class MessageCleanerTest : StringSpec({

    // ---- Scenario 1: adjacent same-speaker merge -------------------------

    "adjacent messages from same speaker are merged" {
        val rules = CleaningRules(mergeAdjacentSameSpeaker = true, filterPatterns = emptyList())
        val cleaner = MessageCleaner(rules)
        val input =
            listOf(
                raw("第一句", "张三", 0),
                raw("第二句", "张三", 1),
                raw("第三句", "李四", 2),
            )
        val result = cleaner.clean(input)

        result shouldHaveSize 2
        result[0].content shouldBe "第一句 第二句"
        result[0].rawSpeakerLabel shouldBe "张三"
        result[1].content shouldBe "第三句"
    }

    "merge preserves source index of first message in group" {
        val rules = CleaningRules(mergeAdjacentSameSpeaker = true)
        val input =
            listOf(
                raw("a", "X", 5),
                raw("b", "X", 6),
                raw("c", "X", 7),
            )
        val result = MessageCleaner(rules).clean(input)
        result shouldHaveSize 1
        result[0].sourceIndex shouldBe 5
    }

    "bare lines (null speaker) are NOT merged across speaker boundaries" {
        val rules = CleaningRules(mergeAdjacentSameSpeaker = true)
        val input =
            listOf(
                raw("你好", "张三", 0),
                // bare line — different speaker key (null ≠ "张三", should not merge)
                raw("continuation", null, 1),
                raw("回复", "李四", 2),
            )
        val result = MessageCleaner(rules).clean(input)
        result shouldHaveSize 3 // bare line is its own group (null ≠ "张三")
    }

    // ---- Scenario 2: transfer / red packet filter -----------------------

    "transfer messages are filtered by default rules" {
        val cleaner = MessageCleaner(CleaningRulesLoader.loadDefault())
        val input =
            listOf(
                raw("今天去哪"),
                raw("[转账]", "张三", 1),
                raw("到了", "张三", 2),
            )
        val result = cleaner.clean(input)
        result.none { it.content.contains("[转账]") } shouldBe true
        result.any { it.content == "今天去哪 到了" || it.content.contains("到了") } shouldBe true
    }

    "red packet messages are filtered by default rules" {
        val cleaner = MessageCleaner(CleaningRulesLoader.loadDefault())
        val msgs = listOf(raw("[红包]", "张三", 0), raw("谢谢", "张三", 1))
        val result = cleaner.clean(msgs)
        result.none { it.content.contains("[红包]") } shouldBe true
    }

    // ---- Scenario 3: system prompt filter --------------------------------

    "recall notice is filtered" {
        val cleaner = MessageCleaner(CleaningRulesLoader.loadDefault())
        val msgs =
            listOf(
                raw("你好", "张三", 0),
                raw("撤回了一条消息", "张三", 1),
                raw("再说一遍", "张三", 2),
            )
        val result = cleaner.clean(msgs)
        result.none { it.content.contains("撤回了一条消息") } shouldBe true
    }

    "call duration line is filtered" {
        val cleaner = MessageCleaner(CleaningRulesLoader.loadDefault())
        val msgs = listOf(raw("[通话时长 0:30]", "张三", 0), raw("再聊", "李四", 1))
        val result = cleaner.clean(msgs)
        result.none { it.content.startsWith("[通话时长") } shouldBe true
        result.any { it.content == "再聊" } shouldBe true
    }

    // ---- Scenario 4: link card / URL filter ------------------------------

    "standalone URL is filtered" {
        val rules =
            CleaningRulesLoader.load(
                """
                filter_patterns:
                  - { pattern: "^https?://\\S+${'$'}", type: REGEX }
                """.trimIndent(),
            )
        val cleaner = MessageCleaner(rules)
        val msgs =
            listOf(
                raw("https://example.com/article?q=hello", "张三", 0),
                raw("你看这篇文章", "张三", 1),
            )
        val result = cleaner.clean(msgs)
        result.none { it.content.startsWith("https://") } shouldBe true
        result.any { it.content.contains("文章") } shouldBe true
    }

    "link card tag is filtered by default rules" {
        val cleaner = MessageCleaner(CleaningRulesLoader.loadDefault())
        val msgs = listOf(raw("[链接]", "张三", 0), raw("有意思", "张三", 1))
        val result = cleaner.clean(msgs)
        result.none { it.content == "[链接]" } shouldBe true
    }

    // ---- YAML hot-replace (test swaps rules at runtime) -----------------

    "custom YAML rules override defaults" {
        val yaml =
            """
            merge_adjacent_same_speaker: false
            filter_patterns:
              - { pattern: "CUSTOM_TOKEN", type: EXACT_CONTENT }
            """.trimIndent()
        val rules = CleaningRulesLoader.load(yaml)
        val cleaner = MessageCleaner(rules)
        val msgs =
            listOf(
                raw("CUSTOM_TOKEN", "A", 0),
                raw("keep me", "A", 1),
                // [转账] is not in the custom yaml — should NOT be filtered
                raw("[转账]", "A", 2),
            )
        val result = cleaner.clean(msgs)
        // CUSTOM_TOKEN removed; [转账] kept because custom yaml doesn't list it
        result.none { it.content == "CUSTOM_TOKEN" } shouldBe true
        result.any { it.content == "[转账]" } shouldBe true
        // merge disabled — three messages become two (custom_token removed)
        result shouldHaveSize 2
    }

    "empty input returns empty output" {
        MessageCleaner().clean(emptyList()).shouldBeEmpty()
    }
})
