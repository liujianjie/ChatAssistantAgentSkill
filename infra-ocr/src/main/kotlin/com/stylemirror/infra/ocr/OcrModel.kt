package com.stylemirror.infra.ocr

/**
 * Axis-aligned bounding box of a recognised text region in pixel coordinates.
 *
 * Pure data class (no android.graphics.Rect) so unit tests run on the plain
 * JVM without Robolectric. The android.graphics.Rect → BoundingBox mapping
 * happens once inside [com.stylemirror.infra.ocr.mlkit.MlKitOcrProvider].
 *
 * Coordinates are pixels in the source bitmap's coordinate space.
 * `right` and `bottom` are exclusive — same convention as android.graphics.Rect.
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right ($right) must be >= left ($left)" }
        require(bottom >= top) { "bottom ($bottom) must be >= top ($top)" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * A single recognised line / paragraph from OCR.
 *
 * @param text Recognised text content. Already stripped of trailing whitespace.
 * @param bounds Pixel-space bounding box in the source bitmap.
 * @param confidence Provider-reported confidence in `[0,1]`, or `null` when
 *   the provider does not expose per-line confidence (e.g. ML Kit returns
 *   confidence at the symbol level only — we deliberately do not derive a
 *   line-level number from it because the aggregation rule is provider-specific).
 */
data class TextBox(
    val text: String,
    val bounds: BoundingBox,
    val confidence: Float? = null,
) {
    init {
        confidence?.let {
            require(it in 0.0f..1.0f) { "confidence must be in [0,1], was $it" }
        }
    }
}

/**
 * Result of a single OCR call. [textBoxes] preserves the order the provider
 * returns; downstream consumers (T18 Soul adapter) re-sort by Y-coordinate.
 */
data class OcrResult(val textBoxes: List<TextBox>) {
    val isEmpty: Boolean get() = textBoxes.isEmpty()
}
