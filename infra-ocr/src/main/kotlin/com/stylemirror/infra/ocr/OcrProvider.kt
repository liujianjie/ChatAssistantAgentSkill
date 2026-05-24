package com.stylemirror.infra.ocr

import android.graphics.Bitmap
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.Outcome

/**
 * Abstraction over text-recognition backends so the rest of the app never
 * couples to a concrete OCR vendor.
 *
 * ## Implementation strategy (SPEC §1.5 / ADR-0004)
 *
 * * **Default**: [com.stylemirror.infra.ocr.mlkit.MlKitOcrProvider] — on-device
 *   ML Kit Chinese text recognition. Free, offline, ≥ 95% accuracy on the
 *   Soul-screenshot test set (see T16 acceptance criteria).
 * * **Reserved**: PaddleOCR / cloud OCR providers exist as named stubs
 *   returning [DomainError.NotImplemented] so we can swap them in later
 *   without touching call sites.
 *
 * ## Error semantics
 *
 * * [DomainError.OcrFailure] for runtime failures (image unreadable,
 *   provider error, no text detected).
 * * [DomainError.NotImplemented] only from explicit stub providers — never
 *   from the production code path.
 *
 * Empty results are returned as `Outcome.Err(OcrFailure(NO_TEXT_DETECTED))`
 * rather than `Outcome.Ok(emptyList())` — callers don't have to special-case
 * "succeeded but useless".
 */
interface OcrProvider {
    suspend fun recognize(image: Bitmap): Outcome<OcrResult, DomainError>
}
