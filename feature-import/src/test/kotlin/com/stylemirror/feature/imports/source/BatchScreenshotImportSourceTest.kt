package com.stylemirror.feature.imports.source

import com.stylemirror.feature.realtime.platform.ClassifiedTextBox
import com.stylemirror.feature.realtime.platform.PlatformAdapter
import com.stylemirror.feature.realtime.platform.Speaker
import com.stylemirror.infra.ocr.BoundingBox
import com.stylemirror.infra.ocr.OcrResult
import com.stylemirror.infra.ocr.TextBox
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private fun box(
    text: String,
    left: Int = 0,
    right: Int = 200,
): TextBox = TextBox(text = text, bounds = BoundingBox(left = left, top = 0, right = right, bottom = 40))

/**
 * Hand-rolled adapter that returns a fixed classification list — keeps the
 * test focused on the source's mapping logic instead of the Soul algorithm
 * (which has its own test suite).
 */
private class StaticAdapter(private val labels: List<Speaker>) : PlatformAdapter {
    override fun classifySpeakers(
        imageWidth: Int,
        ocr: OcrResult,
    ): List<ClassifiedTextBox> = ocr.textBoxes.zip(labels).map { (b, s) -> ClassifiedTextBox(b, s) }
}

class BatchScreenshotImportSourceTest : StringSpec({

    "toRawMessages assigns 我 / 对方 labels per Speaker" {
        val ocr =
            OcrResult(
                listOf(
                    box("你好", left = 0, right = 200),
                    box("好的", left = 800, right = 1020),
                ),
            )
        val adapter = StaticAdapter(listOf(Speaker.THEIRS, Speaker.ME))
        val msgs = BatchScreenshotImportSource.toRawMessages(1080, ocr, adapter)
        msgs shouldHaveSize 2
        msgs[0].rawSpeakerLabel shouldBe "对方"
        msgs[0].content shouldBe "你好"
        msgs[1].rawSpeakerLabel shouldBe "我"
        msgs[1].content shouldBe "好的"
    }

    "toRawMessages preserves baseSourceIndex offset" {
        val ocr = OcrResult(listOf(box("一"), box("二")))
        val adapter = StaticAdapter(listOf(Speaker.THEIRS, Speaker.THEIRS))
        val msgs = BatchScreenshotImportSource.toRawMessages(1080, ocr, adapter, baseSourceIndex = 100)
        msgs[0].sourceIndex shouldBe 100
        msgs[1].sourceIndex shouldBe 101
    }

    "toRawMessages trims whitespace from box text" {
        val ocr = OcrResult(listOf(box("  你好  ")))
        val adapter = StaticAdapter(listOf(Speaker.THEIRS))
        BatchScreenshotImportSource.toRawMessages(1080, ocr, adapter).single().content shouldBe "你好"
    }

    "toRawMessages on empty OcrResult returns empty list" {
        val ocr = OcrResult(emptyList())
        BatchScreenshotImportSource.toRawMessages(1080, ocr, StaticAdapter(emptyList())) shouldBe emptyList()
    }
})
