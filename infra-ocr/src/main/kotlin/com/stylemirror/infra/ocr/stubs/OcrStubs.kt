package com.stylemirror.infra.ocr.stubs

import android.graphics.Bitmap
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome
import com.stylemirror.infra.ocr.OcrProvider
import com.stylemirror.infra.ocr.OcrResult

/**
 * Reserved provider stubs for OCR backends we plan to support but have not
 * implemented yet (ADR-0004). They exist to ① pin the package layout so
 * callers can already register them in DI, and ② make a runtime call go
 * down a single, predictable failure path.
 *
 * Calling [recognize] returns [DomainError.NotImplemented]. The contract
 * intentionally diverges from [com.stylemirror.infra.ocr.FakeOcrProvider]
 * which is meant for tests — these stubs would never appear on the
 * test-success path, only on the "wired but not yet built" path.
 */
class PaddleOcrProvider : OcrProvider {
    override suspend fun recognize(image: Bitmap): Outcome<OcrResult, DomainError> = notImplementedResult()
}

class CloudOcrProvider : OcrProvider {
    override suspend fun recognize(image: Bitmap): Outcome<OcrResult, DomainError> = notImplementedResult()
}

internal fun notImplementedResult(): Outcome<OcrResult, DomainError> = Outcome.Err(DomainError.NotImplemented)
