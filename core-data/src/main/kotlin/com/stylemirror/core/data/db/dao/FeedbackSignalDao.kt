package com.stylemirror.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stylemirror.core.data.db.entity.FeedbackSignalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackSignalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(signal: FeedbackSignalEntity)

    @Query("SELECT * FROM feedback_signals ORDER BY created_at_epoch_ms DESC")
    fun observeAll(): Flow<List<FeedbackSignalEntity>>

    @Query("SELECT * FROM feedback_signals ORDER BY created_at_epoch_ms DESC")
    suspend fun findAll(): List<FeedbackSignalEntity>

    @Query(
        "SELECT * FROM feedback_signals WHERE fingerprint_version = :version " +
            "ORDER BY created_at_epoch_ms DESC",
    )
    suspend fun findByFingerprintVersion(version: Int): List<FeedbackSignalEntity>

    @Query("SELECT COUNT(*) FROM feedback_signals")
    suspend fun count(): Int
}
