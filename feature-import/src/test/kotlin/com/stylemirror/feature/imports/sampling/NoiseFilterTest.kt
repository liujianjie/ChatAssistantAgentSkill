package com.stylemirror.feature.imports.sampling

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class NoiseFilterTest : StringSpec({

    // ---- drops -------------------------------------------------------------

    "empty / whitespace-only is noise" {
        NoiseFilter.isNoise("") shouldBe true
        NoiseFilter.isNoise("   ") shouldBe true
        NoiseFilter.isNoise("\n\t  ") shouldBe true
    }

    "pure punctuation (CJK or ASCII) is noise" {
        NoiseFilter.isNoise("。。。") shouldBe true
        NoiseFilter.isNoise("...") shouldBe true
        NoiseFilter.isNoise("？？？") shouldBe true
        NoiseFilter.isNoise("!!!") shouldBe true
        NoiseFilter.isNoise("，。、；") shouldBe true
    }

    "all-emoji message (no textual content) is noise" {
        NoiseFilter.isNoise("😄😄😄") shouldBe true
        NoiseFilter.isNoise("👍🏻") shouldBe true
    }

    "single-char filler (嗯/哦/啊/...) is noise" {
        NoiseFilter.isNoise("嗯") shouldBe true
        NoiseFilter.isNoise("哦") shouldBe true
        NoiseFilter.isNoise("啊") shouldBe true
        NoiseFilter.isNoise("好") shouldBe true
        NoiseFilter.isNoise("对") shouldBe true
        NoiseFilter.isNoise("行") shouldBe true
    }

    // ---- keeps (these MUST survive — they are style signal) -----------------

    "distinctive short words are kept" {
        NoiseFilter.isNoise("确实") shouldBe false
        NoiseFilter.isNoise("离谱") shouldBe false
        NoiseFilter.isNoise("绷不住") shouldBe false
        NoiseFilter.isNoise("活该") shouldBe false
    }

    "repeated emotional cadence is kept" {
        NoiseFilter.isNoise("哈哈哈") shouldBe false
        NoiseFilter.isNoise("呜呜呜") shouldBe false
        NoiseFilter.isNoise("嗯嗯嗯") shouldBe false // 3-char repetition isn't single-char stopword
    }

    "single-char content word is kept (not a stopword)" {
        NoiseFilter.isNoise("我") shouldBe false
        NoiseFilter.isNoise("你") shouldBe false
        NoiseFilter.isNoise("怎") shouldBe false
    }

    "emoji + text is kept (text wins)" {
        NoiseFilter.isNoise("哈哈😂") shouldBe false
        NoiseFilter.isNoise("🌙晚安") shouldBe false
    }

    "long sentence is always kept" {
        NoiseFilter.isNoise("我今天去了一趟超市，发现香蕉降价了") shouldBe false
        NoiseFilter.isNoise("This is a regular English sentence.") shouldBe false
    }
})
