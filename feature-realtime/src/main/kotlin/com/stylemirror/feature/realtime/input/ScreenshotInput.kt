package com.stylemirror.feature.realtime.input

import android.graphics.Bitmap
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.OcrFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.infra.ocr.OcrProvider
import com.stylemirror.infra.ocr.OcrResult
import com.stylemirror.infra.ocr.TextBox

/**
 * Screenshot-to-text capture path: OCR a bitmap, then join the recognised
 * lines into a single transcript string the user can drop into the existing
 * paste flow.
 *
 * ## Why not implement [InputAdapter] yet
 *
 * Producing a [com.stylemirror.domain.conversation.ConversationContext]
 * directly would require speaker classification ("我" vs "对方") from the
 * screenshot — that's T18's PlatformAdapter job. Until then, the user's
 * own paste box is the speaker-disambiguation surface: we extract the text
 * and let them proofread.
 *
 * ## Empty result handling
 *
 * If [OcrProvider.recognize] succeeds with zero boxes, this class returns
 * the same `OcrFailure(NO_TEXT_DETECTED)` so the UI can show a "no text
 * found" message rather than silently filling an empty paste box.
 */
class ScreenshotInput(
    private val ocrProvider: OcrProvider,
) {
    suspend fun captureFrom(image: Bitmap): Outcome<String, DomainError> = mapResult(ocrProvider.recognize(image))

    companion object {
        /** Pure mapping step factored out so tests can exercise it without a [Bitmap]. */
        internal fun mapResult(ocr: Outcome<OcrResult, DomainError>): Outcome<String, DomainError> =
            when (ocr) {
                is Outcome.Err -> ocr
                is Outcome.Ok ->
                    if (ocr.value.isEmpty) {
                        Outcome.Err(DomainError.OcrFailure(OcrFailureReason.NO_TEXT_DETECTED))
                    } else {
                        Outcome.Ok(formatOcrText(ocr.value))
                    }
            }

        /**
         * Joins recognised text boxes into a single newline-separated transcript.
         *
         * Sort order: top-to-bottom by the box's vertical centre, then
         * left-to-right by horizontal centre as a tie-breaker. This matches
         * how chat clients render messages and keeps consecutive bubbles in
         * the right order even when ML Kit returns them out of geometric
         * sequence (it usually does not, but the sort is cheap insurance).
         */
        fun formatOcrText(result: OcrResult): String =
            result.textBoxes
                .sortedWith(compareBy({ it.bounds.centerY }, { it.bounds.centerX }))
                .joinToString(separator = "\n") { it.text.trim() }
                .trim()

        @Suppress("unused") // exposed for tests / debugging
        internal fun firstBox(result: OcrResult): TextBox? = result.textBoxes.firstOrNull()
    }
}
