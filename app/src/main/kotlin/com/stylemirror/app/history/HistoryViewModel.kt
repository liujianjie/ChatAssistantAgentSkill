package com.stylemirror.app.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylemirror.app.onboarding.StyleFingerprintSummary
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.profiling.FingerprintJson
import com.stylemirror.core.data.repository.StyleFingerprintStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class HistoryItem(
    val version: Int,
    val createdAtEpochMs: Long,
    val sampleSize: Int,
    val summary: StyleFingerprintSummary?,
    val isLatest: Boolean,
)

sealed class RollbackState {
    data object Idle : RollbackState()

    data object Working : RollbackState()

    data class Done(val newVersion: Int) : RollbackState()

    data class Error(val message: String) : RollbackState()
}

/**
 * Lists the user's [StyleFingerprintEntity] versions and lets them roll
 * back to an earlier one.
 *
 * Per IncrementalLearner's contract, rollback never deletes history — it
 * copies the chosen version's JSON forward as a new entity with the next
 * version number. RoomBackedStyleEngine.findLatest() then picks it up.
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val store: StyleFingerprintStore,
    ) : ViewModel() {
        val items: StateFlow<List<HistoryItem>> =
            store.observeHistory(partnerScopeId = null)
                .map { entities ->
                    val latestVersion = entities.maxOfOrNull { it.version }
                    entities
                        .sortedByDescending { it.version }
                        .map { it.toItem(isLatest = it.version == latestVersion) }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = emptyList(),
                )

        private val _rollback = MutableStateFlow<RollbackState>(RollbackState.Idle)
        val rollback: StateFlow<RollbackState> = _rollback.asStateFlow()

        fun rollbackTo(version: Int) {
            if (_rollback.value is RollbackState.Working) return
            _rollback.value = RollbackState.Working
            viewModelScope.launch {
                runCatching {
                    val source =
                        store.findByVersion(version)
                            ?: error("版本 $version 不存在")
                    val newVersion = store.nextVersion()
                    store.insert(
                        StyleFingerprintEntity(
                            version = newVersion,
                            createdAtEpochMs = Instant.now().toEpochMilli(),
                            sampleSize = source.sampleSize,
                            partnerScopeId = source.partnerScopeId,
                            fingerprintJson = source.fingerprintJson,
                        ),
                    )
                    newVersion
                }.onSuccess { newVersion ->
                    _rollback.value = RollbackState.Done(newVersion = newVersion)
                }.onFailure { e ->
                    _rollback.value = RollbackState.Error(message = e.message ?: "回滚失败")
                }
            }
        }

        fun acknowledgeRollback() {
            _rollback.value = RollbackState.Idle
        }

        private fun StyleFingerprintEntity.toItem(isLatest: Boolean): HistoryItem {
            val summary =
                runCatching { FingerprintJson.fromJson(fingerprintJson) }
                    .map { StyleFingerprintSummary.of(it, sampleCount = sampleSize) }
                    .getOrNull()
            return HistoryItem(
                version = version,
                createdAtEpochMs = createdAtEpochMs,
                sampleSize = sampleSize,
                summary = summary,
                isLatest = isLatest,
            )
        }

        companion object {
            private const val STOP_TIMEOUT_MS: Long = 5_000
        }
    }
