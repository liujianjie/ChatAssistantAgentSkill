package com.stylemirror.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent record of a single chat message.
 *
 * Privacy red line: no field may contain wechat_id, phone number, or any
 * real-identity reference. [partnerId] is an opaque app-assigned handle that
 * never leaves the device.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "partner_id")
    val partnerId: String,
    @ColumnInfo(name = "content")
    val content: String,
    /** "ME" or "THEIRS" — mirrors [com.stylemirror.domain.conversation.Message] sealed subtype. */
    @ColumnInfo(name = "speaker")
    val speaker: String,
    /** Null for ME messages (we don't store our own display name here). */
    @ColumnInfo(name = "display_name")
    val displayName: String?,
    @ColumnInfo(name = "sent_at_epoch_ms")
    val sentAtEpochMs: Long,
    /** Optional import-session association for provenance tracking. */
    @ColumnInfo(name = "import_session_id", index = true)
    val importSessionId: String?,
)
