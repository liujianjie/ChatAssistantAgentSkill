package com.stylemirror.infra.ocr.stubs

import com.stylemirror.domain.error.DomainError
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class OcrStubsTest : StringSpec({

    // We cannot exercise recognize(Bitmap) directly without a real Bitmap,
    // but the helper is the single source of truth for the result, so
    // asserting it covers both stub providers.

    "notImplementedResult yields DomainError.NotImplemented" {
        val result = notImplementedResult()
        result.shouldBeInstanceOf<com.stylemirror.domain.error.Outcome.Err<DomainError>>()
        (result as com.stylemirror.domain.error.Outcome.Err).error
            .shouldBeInstanceOf<DomainError.NotImplemented>()
    }

    "PaddleOcrProvider and CloudOcrProvider can be instantiated" {
        // No exception on construction; this guards the DI-registry use case
        // (caller wires it up before realizing it isn't implemented yet).
        PaddleOcrProvider()
        CloudOcrProvider()
    }
})
