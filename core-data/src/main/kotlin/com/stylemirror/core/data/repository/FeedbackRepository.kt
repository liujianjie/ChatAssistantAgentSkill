package com.stylemirror.core.data.repository

import com.stylemirror.core.data.db.dao.FeedbackSignalDao
import com.stylemirror.core.data.db.entity.FeedbackSignalEntity
import com.stylemirror.domain.feedback.CandidateId
import com.stylemirror.domain.feedback.DiscardReason
import com.stylemirror.domain.feedback.FeedbackSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Persists and retrieves [FeedbackSignal]s. Replaces [FeedbackBuffer] (T20).
 *
 * Domain ↔ entity mapping is done here; the DAO layer knows only about
 * [FeedbackSignalEntity] strings.
 */
class FeedbackRepository(private val dao: FeedbackSignalDao) {
    suspend fun record(
        id: String,
        signal: FeedbackSignal,
    ) {
        dao.insert(signal.toEntity(id))
    }

    fun observeAll(): Flow<List<FeedbackSignal>> =
        dao.observeAll().map { entities -> entities.mapNotNull { it.toDomain() } }

    suspend fun findAll(): List<FeedbackSignal> = dao.findAll().mapNotNull { it.toDomain() }

    suspend fun count(): Int = dao.count()

    suspend fun findByFingerprintVersion(version: Int): List<FeedbackSignal> =
        dao.findByFingerprintVersion(version).mapNotNull { it.toDomain() }
}

private fun FeedbackSignal.toEntity(id: String): FeedbackSignalEntity =
    when (this) {
        is FeedbackSignal.Adopt ->
            FeedbackSignalEntity(
                id = id,
                candidateId = candidateId.value,
                fingerprintVersion = fingerprintVersion,
                type = "ADOPT",
                createdAtEpochMs = createdAt.toEpochMilli(),
                editedContent = null,
                discardReason = null,
            )

        is FeedbackSignal.Modify ->
            FeedbackSignalEntity(
                id = id,
                candidateId = candidateId.value,
                fingerprintVersion = fingerprintVersion,
                type = "MODIFY",
                createdAtEpochMs = createdAt.toEpochMilli(),
                editedContent = editedContent,
                discardReason = null,
            )

        is FeedbackSignal.Discard ->
            FeedbackSignalEntity(
                id = id,
                candidateId = candidateId.value,
                fingerprintVersion = fingerprintVersion,
                type = "DISCARD",
                createdAtEpochMs = createdAt.toEpochMilli(),
                editedContent = null,
                discardReason = reason.name,
            )
    }

private fun FeedbackSignalEntity.toDomain(): FeedbackSignal? {
    val cid = CandidateId(candidateId)
    val ts = Instant.ofEpochMilli(createdAtEpochMs)
    return when (type) {
        "ADOPT" ->
            FeedbackSignal.Adopt(
                candidateId = cid,
                fingerprintVersion = fingerprintVersion,
                createdAt = ts,
            )

        "MODIFY" ->
            editedContent?.let {
                FeedbackSignal.Modify(
                    candidateId = cid,
                    fingerprintVersion = fingerprintVersion,
                    createdAt = ts,
                    editedContent = it,
                )
            }

        "DISCARD" -> {
            val reason =
                discardReason?.let { runCatching { DiscardReason.valueOf(it) }.getOrNull() }
                    ?: DiscardReason.OTHER
            FeedbackSignal.Discard(
                candidateId = cid,
                fingerprintVersion = fingerprintVersion,
                createdAt = ts,
                reason = reason,
            )
        }

        else -> null
    }
}
