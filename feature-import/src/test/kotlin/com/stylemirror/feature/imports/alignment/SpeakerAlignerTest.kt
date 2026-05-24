package com.stylemirror.feature.imports.alignment

import com.stylemirror.feature.imports.source.RawMessage
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

/**
 * Represents a single message in a fixture scenario.
 * [expectedSpeaker] mirrors the fixture's "speaker" field.
 */
private data class FixtureMsg(
    val rawLabel: String?,
    val content: String,
    val expectedSpeaker: SpeakerLabel,
)

private fun raw(
    label: String?,
    content: String,
    idx: Int = 0,
) = RawMessage(rawSpeakerLabel = label, content = content, timestampHint = null, sourceIndex = idx)

private fun buildInput(msgs: List<FixtureMsg>): List<RawMessage> =
    msgs.mapIndexed { i, m -> raw(m.rawLabel, m.content, i) }

private fun errorRate(
    aligned: List<AlignedMessage>,
    expected: List<SpeakerLabel>,
): Double {
    val errors = aligned.zip(expected).count { (a, e) -> a.speaker != e }
    return errors.toDouble() / aligned.size
}

class SpeakerAlignerTest : StringSpec({

    // ---- Fixture 01: 1:1 baseline — 我 alias ---------------------------------

    "fixture-01 1:1 baseline: 我 alias aligns correctly" {
        val aligner = SpeakerAligner(setOf("小明"))
        val msgs =
            listOf(
                FixtureMsg("小明", "在吗", SpeakerLabel.ME),
                FixtureMsg("小红", "在的呀", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "周末有空吗", SpeakerLabel.ME),
                FixtureMsg("小红", "周日下午可以", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "那约个咖啡馆吧", SpeakerLabel.ME),
                FixtureMsg("小红", "你定地点", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "老地方那家可以吗", SpeakerLabel.ME),
                FixtureMsg("小红", "行", SpeakerLabel.THEIRS),
            )
        val result = aligner.align(buildInput(msgs))
        val rate = errorRate(result, msgs.map { it.expectedSpeaker })
        withClue("fixture-01 error rate should be < ${SpeakerAligner.MAX_ERROR_RATE}") {
            rate shouldBeLessThan SpeakerAligner.MAX_ERROR_RATE
        }
        result[0].speaker shouldBe SpeakerLabel.ME
        result[1].speaker shouldBe SpeakerLabel.THEIRS
    }

    // ---- Fixture 02: emoji + mixed language ----------------------------------

    "fixture-02 emoji & mixed: 我 alias still aligns correctly" {
        val aligner = SpeakerAligner(setOf("小明"))
        val msgs =
            listOf(
                FixtureMsg("小明", "晚上好呀😄", SpeakerLabel.ME),
                FixtureMsg("阿杰", "在的", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "今天看到一只猫超可爱🐱😍", SpeakerLabel.ME),
                FixtureMsg("阿杰", "哈哈！让我看看", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "哈哈也是😂晚安喵🌙", SpeakerLabel.ME),
            )
        val result = aligner.align(buildInput(msgs))
        val rate = errorRate(result, msgs.map { it.expectedSpeaker })
        rate shouldBeLessThan SpeakerAligner.MAX_ERROR_RATE
    }

    // ---- Fixture 03: long-short mix -----------------------------------------

    "fixture-03 long-short mix: alignment not affected by message length" {
        val aligner = SpeakerAligner(setOf("小明"))
        val msgs =
            listOf(
                FixtureMsg("小明", "今天去逛了一圈", SpeakerLabel.ME),
                FixtureMsg("小红", "嗯", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "买了件衣服", SpeakerLabel.ME),
                FixtureMsg(
                    "小红",
                    "那件衣服的颜色怎么样？适合什么场合穿？",
                    SpeakerLabel.THEIRS,
                ),
                FixtureMsg("小明", "好看", SpeakerLabel.ME),
                FixtureMsg("小红", "那就好", SpeakerLabel.THEIRS),
            )
        val result = aligner.align(buildInput(msgs))
        errorRate(result, msgs.map { it.expectedSpeaker }) shouldBeLessThan SpeakerAligner.MAX_ERROR_RATE
    }

    // ---- Fixture 04: cross-device (alias change mid-conversation) -----------

    "fixture-04 cross-device: same partner appears as 小杰 then Jay" {
        val aligner = SpeakerAligner(setOf("小明"))
        // Both 小杰 and Jay are theirs — aligner just needs to NOT classify them as me
        val msgs =
            listOf(
                FixtureMsg("小明", "早", SpeakerLabel.ME),
                FixtureMsg("小杰", "早安", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "你昨晚改完简历了吗", SpeakerLabel.ME),
                FixtureMsg("小杰", "改了一半", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "中午好 你吃了吗", SpeakerLabel.ME),
                // display name changed to Jay on company device
                FixtureMsg("Jay", "just had lunch", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "你怎么换成英文名了", SpeakerLabel.ME),
                FixtureMsg("Jay", "公司电脑客户端是英文的", SpeakerLabel.THEIRS),
            )
        val result = aligner.align(buildInput(msgs))
        val rate = errorRate(result, msgs.map { it.expectedSpeaker })
        withClue("fixture-04 cross-device error rate: $rate") {
            rate shouldBeLessThan SpeakerAligner.MAX_ERROR_RATE
        }
    }

    // ---- Fixture 05: nickname change ----------------------------------------

    "fixture-05 nickname change: user's alias changes mid-conversation" {
        val aligner = SpeakerAligner(setOf("小明", "Ming"))
        val msgs =
            listOf(
                FixtureMsg("小明", "周末见吧", SpeakerLabel.ME),
                FixtureMsg("小丽", "好的", SpeakerLabel.THEIRS),
                // Later the user switches to "Ming" alias on another device
                FixtureMsg("Ming", "到了", SpeakerLabel.ME),
                FixtureMsg("小丽", "等你", SpeakerLabel.THEIRS),
                FixtureMsg("Ming", "五分钟", SpeakerLabel.ME),
            )
        val result = aligner.align(buildInput(msgs))
        val rate = errorRate(result, msgs.map { it.expectedSpeaker })
        rate shouldBeLessThan SpeakerAligner.MAX_ERROR_RATE
    }

    // ---- Fixture 06: group chat — 3 theirs ----------------------------------

    "fixture-06 group chat: multiple theirs identities all classified correctly" {
        val aligner = SpeakerAligner(setOf("小明"))
        val msgs =
            listOf(
                FixtureMsg("小明", "大家周末有空吗", SpeakerLabel.ME),
                FixtureMsg("小红", "周六可以", SpeakerLabel.THEIRS),
                FixtureMsg("小李", "周日更好", SpeakerLabel.THEIRS),
                FixtureMsg("小张", "我都行", SpeakerLabel.THEIRS),
                FixtureMsg("小明", "那就周六吧", SpeakerLabel.ME),
                FixtureMsg("小红", "ok", SpeakerLabel.THEIRS),
                FixtureMsg("小李", "好", SpeakerLabel.THEIRS),
                FixtureMsg("小张", "没问题", SpeakerLabel.THEIRS),
            )
        val result = aligner.align(buildInput(msgs))
        val rate = errorRate(result, msgs.map { it.expectedSpeaker })
        rate shouldBeLessThan SpeakerAligner.MAX_ERROR_RATE
        // All three theirs identities must be labelled THEIRS
        result.filter { it.speaker == SpeakerLabel.THEIRS }
            .map { it.displayName }
            .toSet() shouldBe setOf("小红", "小李", "小张")
    }

    // ---- Fixture 07: group with aliases — alias collision stress test -------

    "fixture-07 alias collision: Lily vs Liam — no false-positive Me" {
        // User's aliases: Lily, Lily🌿, 莉莉
        // Other participants include "Liam" — must NOT match user's aliases
        val aligner = SpeakerAligner(setOf("Lily", "Lily🌿", "莉莉"))
        val msgs =
            listOf(
                FixtureMsg("Lily", "大家好", SpeakerLabel.ME),
                // collision risk: "Liam" starts with "Li" but is NOT an alias
                FixtureMsg("Liam", "你好", SpeakerLabel.THEIRS),
                FixtureMsg("Lily🌿", "周末活动有人参加吗", SpeakerLabel.ME),
                FixtureMsg("Bob", "我去", SpeakerLabel.THEIRS),
                FixtureMsg("莉莉", "太好了", SpeakerLabel.ME),
                FixtureMsg("Liam", "我也去", SpeakerLabel.THEIRS),
            )
        val result = aligner.align(buildInput(msgs))
        val rate = errorRate(result, msgs.map { it.expectedSpeaker })
        withClue("fixture-07 alias collision error rate: $rate") {
            rate shouldBeLessThan SpeakerAligner.MAX_ERROR_RATE
        }
        // Liam must not be classified as ME
        result.filter { it.rawMessage.rawSpeakerLabel == "Liam" }
            .all { it.speaker == SpeakerLabel.THEIRS } shouldBe true
    }

    // ---- Bare-line inheritance -------------------------------------------

    "bare lines inherit previous speaker" {
        val aligner = SpeakerAligner(setOf("我"))
        val msgs =
            listOf(
                raw("我", "第一句", 0),
                // bare line — should inherit ME from previous
                raw(null, "续句", 1),
                raw("对方", "回复", 2),
                // bare line — should inherit THEIRS from previous
                raw(null, "续回复", 3),
            )
        val result = aligner.align(msgs)
        result[0].speaker shouldBe SpeakerLabel.ME
        result[1].speaker shouldBe SpeakerLabel.ME
        result[2].speaker shouldBe SpeakerLabel.THEIRS
        result[3].speaker shouldBe SpeakerLabel.THEIRS
    }

    "first bare line defaults to THEIRS (conservative)" {
        val aligner = SpeakerAligner(setOf("我"))
        val msgs = listOf(raw(null, "首句无前缀", 0))
        aligner.align(msgs).single().speaker shouldBe SpeakerLabel.THEIRS
    }

    "empty myAliases labels everything THEIRS" {
        val aligner = SpeakerAligner(emptySet())
        val msgs =
            listOf(
                raw("小明", "hello", 0),
                raw("小红", "world", 1),
            )
        aligner.align(msgs).all { it.speaker == SpeakerLabel.THEIRS } shouldBe true
    }

    "displayName is null for ME and set for THEIRS" {
        val aligner = SpeakerAligner(setOf("我"))
        val msgs =
            listOf(
                raw("我", "mine", 0),
                raw("对方", "theirs", 1),
            )
        val result = aligner.align(msgs)
        result[0].displayName shouldBe null
        result[1].displayName shouldBe "对方"
    }

    "empty message list returns empty result" {
        SpeakerAligner(setOf("我")).align(emptyList()) shouldBe emptyList()
    }
})
