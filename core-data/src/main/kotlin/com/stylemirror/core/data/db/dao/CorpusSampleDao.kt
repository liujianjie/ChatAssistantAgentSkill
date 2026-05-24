package com.stylemirror.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorpusSampleDao {
    @Insert
    suspend fun insertAll(samples: List<CorpusSampleEntity>): List<Long>

    /** Active (non-soft-deleted) samples for a given fingerprint version. */
    @Query(
        "SELECT * FROM style_corpus_samples " +
            "WHERE fingerprint_version = :version AND deleted_at_epoch_ms IS NULL",
    )
    suspend fun findActiveByVersion(version: Int): List<CorpusSampleEntity>

    /** All samples (including deleted) — used by export/import to preserve user state. */
    @Query("SELECT * FROM style_corpus_samples WHERE fingerprint_version = :version")
    suspend fun findAllByVersion(version: Int): List<CorpusSampleEntity>

    @Query(
        "SELECT * FROM style_corpus_samples " +
            "WHERE fingerprint_version = :version AND deleted_at_epoch_ms IS NULL " +
            "ORDER BY scenario, rowid",
    )
    fun observeActiveByVersion(version: Int): Flow<List<CorpusSampleEntity>>

    @Query(
        "UPDATE style_corpus_samples SET deleted_at_epoch_ms = :nowEpochMs " +
            "WHERE rowid = :rowId AND deleted_at_epoch_ms IS NULL",
    )
    suspend fun softDelete(
        rowId: Long,
        nowEpochMs: Long,
    ): Int

    @Query("UPDATE style_corpus_samples SET deleted_at_epoch_ms = NULL WHERE rowid = :rowId")
    suspend fun undelete(rowId: Long): Int
}
