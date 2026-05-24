package com.stylemirror.infra.ocr

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class BoundingBoxTest : StringSpec({

    "rejects right < left" {
        shouldThrow<IllegalArgumentException> { BoundingBox(left = 10, top = 0, right = 5, bottom = 20) }
    }

    "rejects bottom < top" {
        shouldThrow<IllegalArgumentException> { BoundingBox(left = 0, top = 30, right = 10, bottom = 20) }
    }

    "computes width / height / center" {
        val box = BoundingBox(left = 10, top = 20, right = 30, bottom = 60)
        box.width shouldBe 20
        box.height shouldBe 40
        box.centerX shouldBe 20
        box.centerY shouldBe 40
    }

    "allows degenerate zero-size box (left==right, top==bottom)" {
        val box = BoundingBox(left = 5, top = 5, right = 5, bottom = 5)
        box.width shouldBe 0
        box.height shouldBe 0
    }
})
