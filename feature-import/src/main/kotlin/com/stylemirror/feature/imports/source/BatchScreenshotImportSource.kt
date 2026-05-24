package com.stylemirror.feature.imports.source

import android.graphics.Bitmap
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.OcrFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.feature.realtime.platform.PlatformAdapter
import com.stylemirror.feature.realtime.platform.Speaker
import com.stylemirror.infra.ocr.OcrProvider
import com.stylemirror.infra.ocr.OcrResult
import com.stylemirror.infra.ocr.TextBox
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * One screenshot to be batched through OCR.
 *
 * @property bitmap Decoded bitmap. Caller is responsible for recycling it
 *   after the batch is complete (we never call `bitmap.recycle()` during
 *   stream because Compose / lifecycle may still hold a ref).
 * @property imageWidth Pixel width of the bitmap. Pre-computed so the
 *   classifier can run before the bitmap is decoded for OCR (and so tests
 *   don't need a real Bitmap).
 */
data class ScreenshotPayload(
    val bitmap: Bitmap,
    val imageWidth: Int,
)

/**
 * [ImportSource] that runs OCR over a batch of screenshots and uses a
 * [PlatformAdapter] to label each line "我" / "对方" before emitting it
 * downstream as a [RawMessage].
 *
 * ## Pipeline per screenshot
 *
 * 1. `ocrProvider.recognize(bitmap)` → [OcrResult]
 * 2. `platformAdapter.classifySpeakers(width, ocr)` → speaker per box
 * 3. Map each classified box to a [RawMessage] with `rawSpeakerLabel`
 *    set to "我" or "对方" so downstream [SpeakerAligner] picks them up.
 *
 * Failed OCR on a single screenshot is **non-fatal** for the batch — the
 * stream skips it and reports the failure via [onError]. This matches the
 * "50 screenshots, one is blurry" real-world case: we'd rather get 49
 * useful screenshots into the corpus than abort everything.
 *
 * Cancellation: each emit checks `coroutineContext.ensureActive()`, so a
 * collector that times out / cancels promptly stops further OCR work.
 *
 * Progress: [onProgress] is invoked on every screenshot boundary with the
 * 1-based count and total. Suitable for driving a Compose progress bar.
 */
class BatchScreenshotImportSource(
    private val screenshots: List<ScreenshotPayload>,
    private val ocrProvider: OcrProvider,
    private val platformAdapter: PlatformAdapter,
    private val onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    private val onError: (index: Int, error: DomainError) -> Unit = { _, _ -> },
) : ImportSource {
    override fun stream(): Flow<RawMessage> =
        flow {
            coroutineScope {
                var globalIndex = 0
                screenshots.forEachIndexed { i, payload ->
                    ensureActive()
                    val emittedHere =
                        when (val result = ocrProvider.recognize(payload.bitmap)) {
                            is Outcome.Err -> {
                                onError(i, result.error)
                                0
                            }

                            is Outcome.Ok ->
                                if (result.value.isEmpty) {
                                    onError(i, DomainError.OcrFailure(OcrFailureReason.NO_TEXT_DETECTED))
                                    0
                                } else {
                                    val rawMessages =
                                        toRawMessages(
                                            imageWidth = payload.imageWidth,
                                            ocr = result.value,
                                            platformAdapter = platformAdapter,
                                            baseSourceIndex = globalIndex,
                                        )
                                    rawMessages.forEach { emit(it) }
                                    rawMessages.size
                                }
                        }
                    globalIndex += emittedHere
                    onProgress(i + 1, screenshots.size)
                }
            }
        }

    companion object {
        const val ME_LABEL: String = "我"
        const val THEIRS_LABEL: String = "对方"

        /**
         * Pure mapping from a single screenshot's OCR result to [RawMessage]s.
         * Extracted so unit tests can exercise the speaker-label assignment
         * without Bitmap / Robolectric.
         *
         * @param baseSourceIndex Starting source index. Each emitted
         *   RawMessage gets `baseSourceIndex + i` so indexes are unique
         *   across the whole batch.
         */
        fun toRawMessages(
            imageWidth: Int,
            ocr: OcrResult,
            platformAdapter: PlatformAdapter,
            baseSourceIndex: Int = 0,
        ): List<RawMessage> {
            val classified = platformAdapter.classifySpeakers(imageWidth, ocr)
            return classified.mapIndexed { i, c ->
                rawMessageFor(c.textBox, c.speaker, baseSourceIndex + i)
            }
        }

        private fun rawMessageFor(
            box: TextBox,
            speaker: Speaker,
            sourceIndex: Int,
        ): RawMessage =
            RawMessage(
                rawSpeakerLabel =
                    when (speaker) {
                        Speaker.ME -> ME_LABEL
                        Speaker.THEIRS -> THEIRS_LABEL
                    },
                content = box.text.trim(),
                timestampHint = null,
                sourceIndex = sourceIndex,
            )
    }
}
