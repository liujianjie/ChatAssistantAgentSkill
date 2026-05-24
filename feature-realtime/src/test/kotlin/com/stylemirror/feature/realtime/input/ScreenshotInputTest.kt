package com.stylemirror.feature.realtime.input

import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.OcrFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.infra.ocr.BoundingBox
import com.stylemirror.infra.ocr.OcrResult
import com.stylemirror.infra.ocr.TextBox
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun box(
    text: String,
    top: Int,
    left: Int = 0,
): TextBox = TextBox(text = text, bounds = BoundingBox(left = left, top = top, right = left + 200, bottom = top + 40))

class ScreenshotInputTest : StringSpec({

    "formatOcrText sorts by Y then X and joins with newlines" {
        val result =
            OcrResult(
                listOf(
                    box("第二行", top = 100),
                    box("第一行", top = 50),
                    box("同行右", top = 50, left = 300),
                ),
            )
        ScreenshotInput.formatOcrText(result) shouldBe "第一行\n同行右\n第二行"
    }

    "formatOcrText trims whitespace inside each line and around result" {
        val result =
            OcrResult(
                listOf(
                    TextBox("  hello  ", BoundingBox(0, 0, 100, 30)),
                    TextBox("  world  ", BoundingBox(0, 50, 100, 80)),
                ),
            )
        ScreenshotInput.formatOcrText(result) shouldBe "hello\nworld"
    }

    "mapResult propagates upstream OcrFailure" {
        val out =
            ScreenshotInput.mapResult(
                Outcome.Err(DomainError.OcrFailure(OcrFailureReason.PROVIDER_ERROR)),
            )
        (out as Outcome.Err).error.shouldBeInstanceOf<DomainError.OcrFailure>()
    }

    "mapResult turns empty OcrResult into NO_TEXT_DETECTED error" {
        val out = ScreenshotInput.mapResult(Outcome.Ok(OcrResult(emptyList())))
        val err = (out as Outcome.Err).error as DomainError.OcrFailure
        err.reason shouldBe OcrFailureReason.NO_TEXT_DETECTED
    }

    "mapResult formats non-empty OcrResult into joined text" {
        val out =
            ScreenshotInput.mapResult(
                Outcome.Ok(
                    OcrResult(
                        listOf(
                            box("你好", top = 50),
                            box("世界", top = 100),
                        ),
                    ),
                ),
            )
        (out as Outcome.Ok).value shouldBe "你好\n世界"
    }
})
