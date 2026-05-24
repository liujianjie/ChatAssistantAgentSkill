package com.stylemirror.core.data.repository

import com.stylemirror.core.data.db.dao.StyleFingerprintDao
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes versioned [StyleFingerprintEntity] records.
 *
 * Domain deserialization (JSON → StyleFingerprint) is intentionally out of
 * scope for this layer — the feature modules own that mapping so the data
 * layer stays infrastructure-only with no business logic dependency.
 */
class StyleFingerprintRepository(private val dao: StyleFingerprintDao) : StyleFingerprintStore {
    override suspend fun insert(entity: StyleFingerprintEntity): Long = dao.insert(entity)

    override suspend fun findLatest(): StyleFingerprintEntity? = dao.findLatest()

    override suspend fun findLatestForScope(partnerScopeId: String?): StyleFingerprintEntity? =
        dao.findLatestForScope(partnerScopeId)

    override fun observeHistory(partnerScopeId: String?): Flow<List<StyleFingerprintEntity>> =
        dao.observeHistory(partnerScopeId)

    override suspend fun findByVersion(version: Int): StyleFingerprintEntity? = dao.findByVersion(version)

    override suspend fun nextVersion(): Int = (dao.maxVersion() ?: 0) + 1
}
