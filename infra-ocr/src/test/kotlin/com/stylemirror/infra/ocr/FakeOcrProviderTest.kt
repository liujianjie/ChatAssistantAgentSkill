package com.stylemirror.infra.ocr

import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.OcrFailureReason
import com.stylemirror.domain.error.Outcome
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest

/**
 * Unit tests run on the JVM with `isReturnDefaultValues = true`, so we
 * cannot construct a real [android.graphics.Bitmap]. Use the test-only
 * `recognizeForTest(null)` entry point to exercise the responder contract
 * without needing Robolectric.
 */
class FakeOcrProviderTest : StringSpec({

    "default responder maps to NO_TEXT_DETECTED" {
        runTest {
            val provider = FakeOcrProvider()
            val result = provider.recognizeForTest(null)
            result.shouldBeInstanceOf<Outcome.Err<DomainError>>()
            val err = (result as Outcome.Err).error
            err.shouldBeInstanceOf<DomainError.OcrFailure>()
            (err as DomainError.OcrFailure).reason shouldBe OcrFailureReason.NO_TEXT_DETECTED
        }
    }

    "custom responder is invoked and result is passed through" {
        runTest {
            val box = BoundingBox(0, 0, 100, 50)
            val provider =
                FakeOcrProvider { _ ->
                    Outcome.Ok(OcrResult(listOf(TextBox(text = "你好", bounds = box))))
                }
            val result = provider.recognizeForTest(null)
            result.shouldBeInstanceOf<Outcome.Ok<OcrResult>>()
            (result as Outcome.Ok).value.textBoxes.first().text shouldBe "你好"
        }
    }

    "responder may surface IMAGE_UNREADABLE error" {
        runTest {
            val provider =
                FakeOcrProvider { _ ->
                    Outcome.Err(DomainError.OcrFailure(OcrFailureReason.IMAGE_UNREADABLE))
                }
            val result = provider.recognizeForTest(null)
            val err = (result as Outcome.Err).error as DomainError.OcrFailure
            err.reason shouldBe OcrFailureReason.IMAGE_UNREADABLE
        }
    }
})
