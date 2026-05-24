package com.stylemirror.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stylemirror.core.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE partner_id = :partnerId ORDER BY sent_at_epoch_ms ASC")
    fun observeByPartner(partnerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE partner_id = :partnerId ORDER BY sent_at_epoch_ms ASC")
    suspend fun findByPartner(partnerId: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE partner_id = :partnerId AND speaker = 'ME'")
    suspend fun countMyMessages(partnerId: String): Int

    @Query("DELETE FROM messages WHERE import_session_id = :sessionId")
    suspend fun deleteByImportSession(sessionId: String)
}
