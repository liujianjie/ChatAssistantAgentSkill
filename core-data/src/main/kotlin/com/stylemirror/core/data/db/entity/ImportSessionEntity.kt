package com.stylemirror.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks a single import run: source type, timing, and result summary.
 * Used for provenance (which messages came from which import) and
 * for the T19 batch-screenshot progress UI.
 *
 * [sourceType] mirrors [com.stylemirror.domain.ImportSourceType]:
 * "PLAIN_TEXT", "BATCH_SCREENSHOT", etc.
 * [completedAtEpochMs] is null while the import is still in progress.
 */
@Entity(tableName = "import_sessions")
data class ImportSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "partner_id")
    val partnerId: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "started_at_epoch_ms")
    val startedAtEpochMs: Long,
    @ColumnInfo(name = "completed_at_epoch_ms")
    val completedAtEpochMs: Long?,
    @ColumnInfo(name = "message_count")
    val messageCount: Int,
)
