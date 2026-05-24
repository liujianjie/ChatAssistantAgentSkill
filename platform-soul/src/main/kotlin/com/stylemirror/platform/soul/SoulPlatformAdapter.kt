package com.stylemirror.platform.soul

import com.stylemirror.feature.realtime.platform.ClassifiedTextBox
import com.stylemirror.feature.realtime.platform.PlatformAdapter
import com.stylemirror.feature.realtime.platform.Speaker
import com.stylemirror.infra.ocr.OcrResult

/**
 * Soul-specific [PlatformAdapter].
 *
 * ## Algorithm (v1, geometry-only)
 *
 * Soul renders self-bubbles right-aligned and other-bubbles left-aligned
 * with consistent margins. We classify each [com.stylemirror.infra.ocr.TextBox]
 * by where its **right edge** sits relative to the screen midline, with a
 * tolerance band that absorbs the natural variance from short messages
 * floating in the centre.
 *
 * - `box.right >= midline + tolerance` → [Speaker.ME]
 * - `box.left  <= midline - tolerance` → [Speaker.THEIRS]
 * - otherwise (rare: short message straddling midline, or system / centred
 *   text) → fall back to last classified speaker, defaulting to
 *   [Speaker.THEIRS] if no prior context exists. Conservative bias so a
 *   centred system banner is not mistakenly attributed as Mine.
 *
 * Tolerance is a fraction of [imageWidth] (default 5%) to keep the rule
 * resolution-independent — Soul renders on phones from 1080px-wide to
 * 3200px-wide and a fixed pixel tolerance would not generalise.
 *
 * ## Why not colour sampling yet
 *
 * Soul's bubble palette includes user-customisable themes (light / dark /
 * brand colours). A pixel-based classifier needs per-theme calibration to
 * stay above 95% accuracy. Geometry alone hits the < 5% mis-alignment
 * target on the test corpus (see T18 acceptance criteria) so we ship
 * geometry-first and keep colour as a follow-up if the error rate climbs.
 *
 * ## Pluggable seam for P1 WeChat adapter
 *
 * The interface is `(imageWidth, OcrResult) → List<ClassifiedTextBox>`.
 * A WeChat adapter ships as a sibling implementation with the same
 * signature; selection happens in DI by inspecting the source app id.
 */
class SoulPlatformAdapter(
    private val tolerancePctOfWidth: Float = DEFAULT_TOLERANCE_PCT,
) : PlatformAdapter {
    init {
        require(tolerancePctOfWidth in 0.0f..0.5f) {
            "tolerancePctOfWidth must be in [0, 0.5], was $tolerancePctOfWidth"
        }
    }

    override fun classifySpeakers(
        imageWidth: Int,
        ocr: OcrResult,
    ): List<ClassifiedTextBox> {
        require(imageWidth > 0) { "imageWidth must be > 0, was $imageWidth" }
        val midline = imageWidth / 2
        val tolerance = (imageWidth * tolerancePctOfWidth).toInt()
        var lastClassified: Speaker? = null
        return ocr.textBoxes.map { box ->
            val speaker =
                when {
                    box.bounds.left >= midline + tolerance -> Speaker.ME
                    box.bounds.right <= midline - tolerance -> Speaker.THEIRS
                    else -> lastClassified ?: Speaker.THEIRS
                }
            lastClassified = speaker
            ClassifiedTextBox(textBox = box, speaker = speaker)
        }
    }

    companion object {
        const val DEFAULT_TOLERANCE_PCT: Float = 0.05f
    }
}
