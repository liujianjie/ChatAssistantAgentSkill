package com.stylemirror.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v2 — One representative message from the user (Speaker.Mine), tagged with a
 * scenario label so [CorpusRetriever] can pick the most relevant samples to
 * inject as few-shot examples in the candidate-generation prompt.
 *
 * Privacy invariant: the type-layer guarantee is that callers building this
 * entity can only feed [com.stylemirror.domain.conversation.Message.Mine]
 * content (see CorpusSampleStore wiring). The DB layer is unaware of that
 * constraint — it's enforced one layer up.
 *
 * Soft-deleted (deletedAt) rather than hard-deleted so users can roll back if
 * they regret pruning a sample. Retrieval queries always filter
 * `deleted_at IS NULL`.
 */
@Entity(
    tableName = "style_corpus_samples",
    indices = [
        Index(value = ["fingerprint_version"]),
        Index(value = ["scenario"]),
    ],
)
data class CorpusSampleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
    /** Fingerprint version this sample was produced under. */
    @ColumnInfo(name = "fingerprint_version")
    val fingerprintVersion: Int,
    /** Optional partner-scope tag, mirrors [StyleFingerprintEntity.partnerScopeId]. */
    @ColumnInfo(name = "partner_scope_id")
    val partnerScopeId: String? = null,
    /** The user's actual message text (already privacy-redacted). */
    @ColumnInfo(name = "text")
    val text: String,
    /**
     * Scenario tag chosen by the profiler:
     * 日常问候 / 调侃 / 拒绝 / 解释 / 安慰 / 冷处理 / 道歉 / 询问 / 其他
     * Free-string for forward compatibility, but profiler should reuse a
     * stable vocabulary across versions.
     */
    @ColumnInfo(name = "scenario")
    val scenario: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    /** Soft-delete timestamp; null when active. */
    @ColumnInfo(name = "deleted_at_epoch_ms")
    val deletedAtEpochMs: Long? = null,
)
