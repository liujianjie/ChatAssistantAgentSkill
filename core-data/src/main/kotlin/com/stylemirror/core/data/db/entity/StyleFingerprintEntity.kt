package com.stylemirror.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Versioned user style fingerprint.
 *
 * The six dimension sub-objects ([com.stylemirror.domain.style.StyleFingerprint])
 * are stored as a JSON blob ([fingerprintJson]) to avoid a 30+ column schema
 * for a single-row-per-version table. The blob is treated as opaque by the
 * persistence layer and deserialized only by the repository.
 *
 * [partnerScopeId] is null for a global profile, or set to a [partnerId]
 * when the fingerprint was conditioned on a specific contact (P1 feature).
 */
@Entity(tableName = "style_fingerprints")
data class StyleFingerprintEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
    @ColumnInfo(name = "version")
    val version: Int,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "sample_size")
    val sampleSize: Int,
    @ColumnInfo(name = "partner_scope_id")
    val partnerScopeId: String?,
    /** JSON-encoded StyleFingerprint dimensions. */
    @ColumnInfo(name = "fingerprint_json")
    val fingerprintJson: String,
)
