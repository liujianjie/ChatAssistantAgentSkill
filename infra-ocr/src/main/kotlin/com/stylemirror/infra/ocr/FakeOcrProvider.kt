package com.stylemirror.infra.ocr

import android.graphics.Bitmap
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.OcrFailureReason
import com.stylemirror.domain.error.Outcome

/**
 * Test / CI implementation of [OcrProvider]. Returns deterministic results
 * with no ML Kit dependency, so unit tests can exercise consumers (T17
 * ScreenshotInput, T18 Soul adapter) without GPU / model files.
 *
 * The default [responder] returns an empty list, mapped to
 * [OcrFailureReason.NO_TEXT_DETECTED]. Tests inject custom responders to
 * stage success / failure / specific text-box layouts.
 *
 * The responder receives a nullable [Bitmap] so tests on a plain JVM
 * (without Robolectric) can pass `null`; production calls always pass a
 * non-null bitmap. Responders that branch on bitmap properties should
 * guard against the test-only `null` case.
 */
class FakeOcrProvider(
    private val responder: (image: Bitmap?) -> Outcome<OcrResult, DomainError> = ::defaultResponder,
) : OcrProvider {
    override suspend fun recognize(image: Bitmap): Outcome<OcrResult, DomainError> = responder(image)

    /** Test-only entry point that bypasses the non-null Bitmap contract. */
    suspend fun recognizeForTest(image: Bitmap?): Outcome<OcrResult, DomainError> = responder(image)

    private companion object {
        @Suppress("UNUSED_PARAMETER")
        fun defaultResponder(image: Bitmap?): Outcome<OcrResult, DomainError> =
            Outcome.Err(DomainError.OcrFailure(OcrFailureReason.NO_TEXT_DETECTED))
    }
}
