package com.stylemirror.core.data.repository

import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for v2 corpus samples (representative user messages
 * tagged by scenario). See ADR-0005 and `docs/ideas/persona-v2.md`.
 *
 * Privacy: callers must only ever pass messages that came from
 * [com.stylemirror.domain.conversation.Message.Mine]; the type-layer guarantee
 * lives one layer up (PersonaProfiler / FingerprintAggregator surface).
 */
interface CorpusSampleStore {
    suspend fun insertAll(samples: List<CorpusSampleEntity>): List<Long>

    suspend fun findActiveByVersion(version: Int): List<CorpusSampleEntity>

    suspend fun findAllByVersion(version: Int): List<CorpusSampleEntity>

    fun observeActiveByVersion(version: Int): Flow<List<CorpusSampleEntity>>

    /** Soft-delete; returns the number of rows updated (0 if already deleted). */
    suspend fun softDelete(
        rowId: Long,
        nowEpochMs: Long,
    ): Int

    suspend fun undelete(rowId: Long): Int
}
