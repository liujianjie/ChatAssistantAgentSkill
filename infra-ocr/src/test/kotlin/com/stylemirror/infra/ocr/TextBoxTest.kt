package com.stylemirror.infra.ocr

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TextBoxTest : StringSpec({

    val anyBox = BoundingBox(left = 0, top = 0, right = 100, bottom = 50)

    "confidence may be null" {
        TextBox(text = "你好", bounds = anyBox).confidence shouldBe null
    }

    "confidence within [0,1] is accepted" {
        TextBox(text = "你好", bounds = anyBox, confidence = 0.0f)
        TextBox(text = "你好", bounds = anyBox, confidence = 1.0f)
        TextBox(text = "你好", bounds = anyBox, confidence = 0.5f).confidence shouldBe 0.5f
    }

    "confidence outside [0,1] is rejected" {
        shouldThrow<IllegalArgumentException> {
            TextBox(text = "你好", bounds = anyBox, confidence = -0.1f)
        }
        shouldThrow<IllegalArgumentException> {
            TextBox(text = "你好", bounds = anyBox, confidence = 1.5f)
        }
    }

    "OcrResult.isEmpty matches the textBoxes list" {
        OcrResult(textBoxes = emptyList()).isEmpty shouldBe true
        OcrResult(textBoxes = listOf(TextBox("hi", anyBox))).isEmpty shouldBe false
    }
})
