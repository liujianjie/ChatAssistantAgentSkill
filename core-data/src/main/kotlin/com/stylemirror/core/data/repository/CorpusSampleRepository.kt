package com.stylemirror.core.data.repository

import com.stylemirror.core.data.db.dao.CorpusSampleDao
import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room-backed implementation of [CorpusSampleStore]. Thin pass-through to the
 * DAO; complex logic (selection, retrieval scoring) lives in feature modules.
 */
class CorpusSampleRepository(private val dao: CorpusSampleDao) : CorpusSampleStore {
    override suspend fun insertAll(samples: List<CorpusSampleEntity>): List<Long> = dao.insertAll(samples)

    override suspend fun findActiveByVersion(version: Int): List<CorpusSampleEntity> = dao.findActiveByVersion(version)

    override suspend fun findAllByVersion(version: Int): List<CorpusSampleEntity> = dao.findAllByVersion(version)

    override fun observeActiveByVersion(version: Int): Flow<List<CorpusSampleEntity>> =
        dao.observeActiveByVersion(version)

    override suspend fun softDelete(
        rowId: Long,
        nowEpochMs: Long,
    ): Int = dao.softDelete(rowId, nowEpochMs)

    override suspend fun undelete(rowId: Long): Int = dao.undelete(rowId)
}
