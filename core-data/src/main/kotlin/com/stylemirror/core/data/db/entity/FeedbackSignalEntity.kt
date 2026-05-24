package com.stylemirror.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted feedback signal for one generated candidate reply.
 *
 * Privacy red line: only the user's own decision (type) and — for MODIFY —
 * the user's own edited text appear here. The other party's messages must
 * never reach this table.
 *
 * [type] is one of "ADOPT", "MODIFY", "DISCARD" (mirrors
 * [com.stylemirror.domain.feedback.FeedbackSignal] sealed subtype).
 * [editedContent] is set only for MODIFY; [discardReason] only for DISCARD.
 */
@Entity(tableName = "feedback_signals")
data class FeedbackSignalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "candidate_id")
    val candidateId: String,
    @ColumnInfo(name = "fingerprint_version")
    val fingerprintVersion: Int,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "edited_content")
    val editedContent: String?,
    @ColumnInfo(name = "discard_reason")
    val discardReason: String?,
)
