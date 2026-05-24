package com.stylemirror.feature.imports.alignment

import com.stylemirror.feature.imports.source.RawMessage

/** Speaker assignment after [SpeakerAligner] resolves a [RawMessage]. */
enum class SpeakerLabel { ME, THEIRS }

/**
 * A [RawMessage] with its speaker resolved to [SpeakerLabel.ME] or
 * [SpeakerLabel.THEIRS]. The original [rawMessage] is retained for
 * provenance; [displayName] carries the other party's label for Theirs messages.
 */
data class AlignedMessage(
    val rawMessage: RawMessage,
    val speaker: SpeakerLabel,
    /** Display name for the other party; null when [speaker] is [SpeakerLabel.ME]. */
    val displayName: String?,
)
