package com.stylemirror.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.stylemirror.core.data.db.entity.ImportSessionEntity

@Dao
interface ImportSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: ImportSessionEntity)

    @Update
    suspend fun update(session: ImportSessionEntity)

    @Query("SELECT * FROM import_sessions ORDER BY started_at_epoch_ms DESC")
    suspend fun findAll(): List<ImportSessionEntity>

    @Query("SELECT * FROM import_sessions WHERE id = :id")
    suspend fun findById(id: String): ImportSessionEntity?

    @Query("SELECT * FROM import_sessions WHERE completed_at_epoch_ms IS NULL")
    suspend fun findInProgress(): List<ImportSessionEntity>
}
