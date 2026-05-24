package com.stylemirror.app.history

import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.repository.StyleFingerprintStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private val VALID_PROFILE_JSON =
    """
    {
      "linguistic": { "formality": "CASUAL", "vocabularyComplexity": 0.3, "sentencePattern": "SHORT_FRAGMENTED", "signaturePhrases": ["哈哈"] },
      "emotional":  { "emojiDensity": 2.0, "exclamationFrequency": 0.4, "tone": "BALANCED", "preferredEmojis": ["😄"] },
      "humor":      { "frequency": 0.5, "types": ["OBSERVATIONAL"] },
      "avoidance":  { "topicsAvoided": [], "hedgingFrequency": 0.2, "deflectionStrategy": "REDIRECT" },
      "pacing":     { "avgMessageLength": 25.0, "avgMessagesPerTurn": 1.5, "responseDelay": "MINUTES" },
      "sensitive":  { "directness": "INDIRECT", "approach": "EMPATHETIC" }
    }
    """.trimIndent()

private fun entity(
    version: Int,
    json: String = VALID_PROFILE_JSON,
    sampleSize: Int = 100,
    createdAtEpochMs: Long = 1_000L * version,
    partnerScopeId: String? = null,
): StyleFingerprintEntity =
    StyleFingerprintEntity(
        rowId = version.toLong(),
        version = version,
        createdAtEpochMs = createdAtEpochMs,
        sampleSize = sampleSize,
        partnerScopeId = partnerScopeId,
        fingerprintJson = json,
    )

private class FakeStore(initial: List<StyleFingerprintEntity> = emptyList()) : StyleFingerprintStore {
    private val state = MutableStateFlow(initial)

    val snapshot: List<StyleFingerprintEntity>
        get() = state.value

    override suspend fun insert(entity: StyleFingerprintEntity): Long {
        state.value = state.value + entity
        return entity.version.toLong()
    }

    override suspend fun findLatest(): StyleFingerprintEntity? = state.value.maxByOrNull { it.version }

    override suspend fun findLatestForScope(partnerScopeId: String?): StyleFingerprintEntity? =
        state.value.filter { it.partnerScopeId == partnerScopeId }.maxByOrNull { it.version }

    override fun observeHistory(partnerScopeId: String?): Flow<List<StyleFingerprintEntity>> {
        // For the unit test we ignore scope filtering; production DAO handles it.
        return state
    }

    override suspend fun findByVersion(version: Int): StyleFingerprintEntity? =
        state.value.firstOrNull { it.version == version }

    override suspend fun nextVersion(): Int = (state.value.maxOfOrNull { it.version } ?: 0) + 1
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest : StringSpec({

    val testDispatcher = StandardTestDispatcher()
    beforeSpec { Dispatchers.setMain(testDispatcher) }
    afterSpec { Dispatchers.resetMain() }

    "items 列出全部版本，按版本号倒序，最新版标记 isLatest" {
        runTest(testDispatcher) {
            val store =
                FakeStore(
                    initial =
                        listOf(
                            entity(version = 1),
                            entity(version = 2),
                            entity(version = 3),
                        ),
                )
            val vm = HistoryViewModel(store = store)
            advanceUntilIdle()

            val items = vm.items.first { it.isNotEmpty() }
            items.map { it.version } shouldBe listOf(3, 2, 1)
            items.first { it.version == 3 }.isLatest shouldBe true
            items.first { it.version == 2 }.isLatest shouldBe false
            items.first { it.summary != null } // 至少一条 summary 解析成功
        }
    }

    "rollbackTo 把旧版 JSON 复制为更新版本号的新条目" {
        runTest(testDispatcher) {
            val oldJson = VALID_PROFILE_JSON
            val newerJson = VALID_PROFILE_JSON.replace("CASUAL", "FORMAL")
            val store =
                FakeStore(
                    initial =
                        listOf(
                            entity(version = 1, json = oldJson, sampleSize = 50),
                            entity(version = 2, json = newerJson, sampleSize = 200),
                        ),
                )
            val vm = HistoryViewModel(store = store)

            vm.rollbackTo(version = 1)
            advanceUntilIdle()

            store.snapshot shouldHaveSize 3
            val latest = store.findLatest()!!
            latest.version shouldBe 3
            latest.fingerprintJson shouldBe oldJson
            latest.sampleSize shouldBe 50
            vm.rollback.value.shouldBeInstanceOf<RollbackState.Done>().newVersion shouldBe 3
        }
    }

    "rollbackTo 不存在的版本进入 Error 状态且不写库" {
        runTest(testDispatcher) {
            val store = FakeStore(initial = listOf(entity(version = 1)))
            val vm = HistoryViewModel(store = store)

            vm.rollbackTo(version = 99)
            advanceUntilIdle()

            store.snapshot shouldHaveSize 1
            vm.rollback.value.shouldBeInstanceOf<RollbackState.Error>()
        }
    }

    "acknowledgeRollback 把 Done/Error 状态拨回 Idle" {
        runTest(testDispatcher) {
            val store = FakeStore(initial = listOf(entity(version = 1), entity(version = 2)))
            val vm = HistoryViewModel(store = store)

            vm.rollbackTo(version = 1)
            advanceUntilIdle()
            vm.rollback.value.shouldBeInstanceOf<RollbackState.Done>()

            vm.acknowledgeRollback()
            vm.rollback.value shouldBe RollbackState.Idle
        }
    }
})
