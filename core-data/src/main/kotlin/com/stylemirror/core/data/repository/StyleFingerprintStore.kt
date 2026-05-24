package com.stylemirror.core.data.repository

import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface contract for [StyleFingerprintRepository], extracted so
 * [PersonaProfiler] and [RoomBackedStyleEngine] can be tested with stubs
 * without depending on Room/Robolectric.
 */
interface StyleFingerprintStore {
    suspend fun insert(entity: StyleFingerprintEntity): Long

    suspend fun findLatest(): StyleFingerprintEntity?

    suspend fun findLatestForScope(partnerScopeId: String?): StyleFingerprintEntity?

    fun observeHistory(partnerScopeId: String?): Flow<List<StyleFingerprintEntity>>

    suspend fun findByVersion(version: Int): StyleFingerprintEntity?

    suspend fun nextVersion(): Int
}
