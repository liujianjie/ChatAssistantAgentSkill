package com.stylemirror.platform.soul

import com.stylemirror.feature.realtime.platform.Speaker
import com.stylemirror.infra.ocr.BoundingBox
import com.stylemirror.infra.ocr.OcrResult
import com.stylemirror.infra.ocr.TextBox
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Synthetic 1080-px wide screenshot. Midline is 540, default tolerance is
 * 54px so right-side bubbles must start at x>=594 and left-side bubbles
 * must end at x<=486 to be unambiguously classified.
 */
private const val IMAGE_WIDTH = 1080

private fun box(
    text: String,
    left: Int,
    right: Int,
    top: Int = 0,
): TextBox = TextBox(text = text, bounds = BoundingBox(left = left, top = top, right = right, bottom = top + 40))

class SoulPlatformAdapterTest : StringSpec({

    val adapter = SoulPlatformAdapter()

    "right-aligned bubble classified as ME" {
        val ocr =
            OcrResult(
                listOf(box("好的我下午到", left = 700, right = 1020)),
            )
        adapter.classifySpeakers(IMAGE_WIDTH, ocr).single().speaker shouldBe Speaker.ME
    }

    "left-aligned bubble classified as THEIRS" {
        val ocr =
            OcrResult(
                listOf(box("你几点到", left = 60, right = 380)),
            )
        adapter.classifySpeakers(IMAGE_WIDTH, ocr).single().speaker shouldBe Speaker.THEIRS
    }

    "centred / straddling bubble inherits previous speaker" {
        // First box is right-aligned ME; second box straddles midline → inherits ME.
        val ocr =
            OcrResult(
                listOf(
                    box("我马上到了", left = 700, right = 1020),
                    box("中央系统提示", left = 470, right = 610, top = 60),
                ),
            )
        val classified = adapter.classifySpeakers(IMAGE_WIDTH, ocr)
        classified[0].speaker shouldBe Speaker.ME
        classified[1].speaker shouldBe Speaker.ME
    }

    "centred bubble defaults to THEIRS when no prior speaker" {
        val ocr =
            OcrResult(
                listOf(box("中央系统提示", left = 470, right = 610)),
            )
        adapter.classifySpeakers(IMAGE_WIDTH, ocr).single().speaker shouldBe Speaker.THEIRS
    }

    "five-message conversation alternates correctly" {
        val ocr =
            OcrResult(
                listOf(
                    box("你来了吗", left = 60, right = 320, top = 0),
                    box("快了", left = 760, right = 950, top = 50),
                    box("路上堵车", left = 700, right = 1020, top = 100),
                    box("好的等你", left = 60, right = 380, top = 150),
                    box("十分钟到", left = 750, right = 1010, top = 200),
                ),
            )
        val speakers = adapter.classifySpeakers(IMAGE_WIDTH, ocr).map { it.speaker }
        speakers shouldBe listOf(Speaker.THEIRS, Speaker.ME, Speaker.ME, Speaker.THEIRS, Speaker.ME)
    }

    "tolerance scales with image width (high-res phone)" {
        val largeWidth = 3200
        val midline = largeWidth / 2
        val tolerance = (largeWidth * 0.05).toInt() // 160
        // Box just barely past midline+tolerance
        val ocr =
            OcrResult(
                listOf(box("我", left = midline + tolerance + 5, right = midline + tolerance + 200)),
            )
        adapter.classifySpeakers(largeWidth, ocr).single().speaker shouldBe Speaker.ME
    }

    "rejects non-positive image width" {
        shouldThrow<IllegalArgumentException> {
            adapter.classifySpeakers(0, OcrResult(emptyList()))
        }
    }

    "rejects out-of-range tolerance at construction" {
        shouldThrow<IllegalArgumentException> { SoulPlatformAdapter(tolerancePctOfWidth = -0.1f) }
        shouldThrow<IllegalArgumentException> { SoulPlatformAdapter(tolerancePctOfWidth = 0.6f) }
    }
})
