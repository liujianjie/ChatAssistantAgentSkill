package com.stylemirror.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StyleFingerprintDao {
    @Insert
    suspend fun insert(fingerprint: StyleFingerprintEntity): Long

    /** Latest version across all scopes (used by the candidate generator). */
    @Query("SELECT * FROM style_fingerprints ORDER BY version DESC LIMIT 1")
    suspend fun findLatest(): StyleFingerprintEntity?

    @Query(
        "SELECT * FROM style_fingerprints WHERE partner_scope_id IS :partnerScopeId " +
            "ORDER BY version DESC LIMIT 1",
    )
    suspend fun findLatestForScope(partnerScopeId: String?): StyleFingerprintEntity?

    @Query(
        "SELECT * FROM style_fingerprints WHERE partner_scope_id IS :partnerScopeId " +
            "ORDER BY version DESC",
    )
    fun observeHistory(partnerScopeId: String?): Flow<List<StyleFingerprintEntity>>

    @Query("SELECT * FROM style_fingerprints WHERE version = :version LIMIT 1")
    suspend fun findByVersion(version: Int): StyleFingerprintEntity?

    @Query("SELECT MAX(version) FROM style_fingerprints")
    suspend fun maxVersion(): Int?
}
