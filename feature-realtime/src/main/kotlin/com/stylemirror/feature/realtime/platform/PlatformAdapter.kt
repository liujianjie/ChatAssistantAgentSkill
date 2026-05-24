package com.stylemirror.feature.realtime.platform

import com.stylemirror.infra.ocr.OcrResult
import com.stylemirror.infra.ocr.TextBox

/**
 * Maps OCR results from a chat-app screenshot to speaker-attributed lines.
 *
 * Each chat platform has its own visual conventions for "self vs other"
 * (Soul: right-aligned blue bubble vs left-aligned grey; WeChat: similar
 * but different palette). [PlatformAdapter] is the seam where that
 * platform-specific knowledge plugs in without leaking into the realtime
 * pipeline.
 *
 * The MVP implementation ([com.stylemirror.platform.soul.SoulPlatformAdapter])
 * uses geometry only — bubble x-position relative to image midline. P1 may
 * add colour sampling for higher accuracy on edge cases (centred system
 * messages, narrow screens, etc.).
 *
 * Implementations are pure transformations: given the same `imageWidth`
 * and `OcrResult`, the same classification must come out. Side effects
 * (logging, color sampling on the bitmap) belong in subclasses, not on
 * the interface contract.
 */
interface PlatformAdapter {
    /**
     * Classify each text box in [ocr] as belonging to [Speaker.ME] or
     * [Speaker.THEIRS]. Order is preserved from [OcrResult.textBoxes].
     *
     * @param imageWidth Width of the source bitmap in pixels — used as
     *   the reference for "right of midline" heuristics.
     */
    fun classifySpeakers(
        imageWidth: Int,
        ocr: OcrResult,
    ): List<ClassifiedTextBox>
}

enum class Speaker { ME, THEIRS }

data class ClassifiedTextBox(
    val textBox: TextBox,
    val speaker: Speaker,
)
