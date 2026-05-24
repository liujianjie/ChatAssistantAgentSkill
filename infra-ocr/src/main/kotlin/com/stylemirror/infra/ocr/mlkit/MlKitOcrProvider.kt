package com.stylemirror.infra.ocr.mlkit

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.OcrFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.infra.ocr.BoundingBox
import com.stylemirror.infra.ocr.OcrProvider
import com.stylemirror.infra.ocr.OcrResult
import com.stylemirror.infra.ocr.TextBox
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device Chinese text recognition powered by Google ML Kit.
 *
 * ## Why Chinese model
 *
 * MVP target is Chinese chat content (WeChat / Soul). The Latin model
 * mis-recognises Chinese glyphs as random Latin characters and the Chinese
 * model includes Latin / digit support, so it covers the common mixed-script
 * case without needing a second recognizer.
 *
 * ## Lifecycle
 *
 * The underlying [TextRecognizer] is process-singleton-friendly — keep one
 * instance per app and let the OS clean it up on process death. We expose a
 * [close] for tests that need to assert resource release deterministically.
 *
 * ## Coordinate space
 *
 * ML Kit returns a `android.graphics.Rect` that's already in the source
 * bitmap's pixel coordinates (it does not auto-rotate based on EXIF when
 * the source is `InputImage.fromBitmap`). We forward those values directly
 * into [BoundingBox].
 */
class MlKitOcrProvider internal constructor(
    private val recognizer: TextRecognizer,
) : OcrProvider, AutoCloseable {
    override suspend fun recognize(image: Bitmap): Outcome<OcrResult, DomainError> =
        suspendCancellableCoroutine { cont ->
            val input =
                runCatching { InputImage.fromBitmap(image, 0) }
                    .getOrElse { e ->
                        cont.resume(Outcome.Err(DomainError.OcrFailure(OcrFailureReason.IMAGE_UNREADABLE, cause = e)))
                        return@suspendCancellableCoroutine
                    }
            recognizer.process(input)
                .addOnSuccessListener { visionText ->
                    val boxes =
                        visionText.textBlocks
                            .flatMap { block -> block.lines }
                            .mapNotNull { line ->
                                val box = line.boundingBox ?: return@mapNotNull null
                                TextBox(
                                    text = line.text,
                                    bounds =
                                        BoundingBox(
                                            left = box.left,
                                            top = box.top,
                                            right = box.right,
                                            bottom = box.bottom,
                                        ),
                                    confidence = null,
                                )
                            }
                    if (boxes.isEmpty()) {
                        cont.resume(Outcome.Err(DomainError.OcrFailure(OcrFailureReason.NO_TEXT_DETECTED)))
                    } else {
                        cont.resume(Outcome.Ok(OcrResult(textBoxes = boxes)))
                    }
                }
                .addOnFailureListener { e ->
                    cont.resume(Outcome.Err(DomainError.OcrFailure(OcrFailureReason.PROVIDER_ERROR, cause = e)))
                }
            cont.invokeOnCancellation { /* ML Kit task does not expose cancel; let it finish silently. */ }
        }

    override fun close() {
        recognizer.close()
    }

    companion object {
        /**
         * Production factory. Pass the application context only if a custom
         * recognizer needs it; the Chinese recognizer does not.
         */
        fun create(): MlKitOcrProvider =
            MlKitOcrProvider(
                recognizer =
                    TextRecognition.getClient(
                        ChineseTextRecognizerOptions.Builder().build(),
                    ),
            )
    }
}
