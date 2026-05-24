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
class StyleFingerprintRepository(private val dao: StyleFingerprintDao) {
    suspend fun insert(entity: StyleFingerprintEntity): Long = dao.insert(entity)

    suspend fun findLatest(): StyleFingerprintEntity? = dao.findLatest()

    suspend fun findLatestForScope(partnerScopeId: String?): StyleFingerprintEntity? =
        dao.findLatestForScope(partnerScopeId)

    fun observeHistory(partnerScopeId: String?): Flow<List<StyleFingerprintEntity>> = dao.observeHistory(partnerScopeId)

    suspend fun findByVersion(version: Int): StyleFingerprintEntity? = dao.findByVersion(version)

    suspend fun nextVersion(): Int = (dao.maxVersion() ?: 0) + 1
}
