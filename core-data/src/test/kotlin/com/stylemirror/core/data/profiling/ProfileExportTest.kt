package com.stylemirror.core.data.profiling

import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.repository.CorpusSampleStore
import com.stylemirror.core.data.repository.StyleFingerprintStore
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val V1_LEGACY_JSON = """
{
  "version": 7,
  "createdAtMs": 1700000000000,
  "sampleSize": 250,
  "partnerScopeId": null,
  "linguistic": { "formality": "CASUAL", "vocabularyComplexity": 0.3, "sentencePattern": "MIXED", "signaturePhrases": ["哈哈"] },
  "emotional":  { "emojiDensity": 2.0, "exclamationFrequency": 0.4, "tone": "BALANCED", "preferredEmojis": ["😄"] },
  "humor":      { "frequency": 0.5, "types": ["OBSERVATIONAL"] },
  "avoidance":  { "topicsAvoided": [], "hedgingFrequency": 0.2, "deflectionStrategy": "REDIRECT" },
  "pacing":     { "avgMessageLength": 25.0, "avgMessagesPerTurn": 1.5, "responseDelay": "MINUTES" },
  "sensitive":  { "directness": "INDIRECT", "approach": "EMPATHETIC" }
}
"""

private class StubStore(initial: List<StyleFingerprintEntity> = emptyList()) : StyleFingerprintStore {
    val inserted = initial.toMutableList()

    override suspend fun insert(entity: StyleFingerprintEntity): Long {
        inserted += entity
        return inserted.size.toLong()
    }

    override suspend fun findLatest() = inserted.lastOrNull()

    override suspend fun findLatestForScope(partnerScopeId: String?) =
        inserted.lastOrNull { it.partnerScopeId == partnerScopeId }

    override fun observeHistory(partnerScopeId: String?) = emptyFlow<List<StyleFingerprintEntity>>()

    override suspend fun findByVersion(version: Int) = inserted.firstOrNull { it.version == version }

    override suspend fun nextVersion(): Int = (inserted.maxOfOrNull { it.version } ?: 0) + 1
}

private class StubCorpusStore(initial: List<CorpusSampleEntity> = emptyList()) : CorpusSampleStore {
    val inserted = initial.toMutableList()

    override suspend fun insertAll(samples: List<CorpusSampleEntity>): List<Long> {
        inserted += samples
        return List(samples.size) { it.toLong() }
    }

    override suspend fun findActiveByVersion(version: Int) =
        inserted.filter { it.fingerprintVersion == version && it.deletedAtEpochMs == null }

    override suspend fun findAllByVersion(version: Int) = inserted.filter { it.fingerprintVersion == version }

    override fun observeActiveByVersion(version: Int) = emptyFlow<List<CorpusSampleEntity>>()

    override suspend fun softDelete(
        rowId: Long,
        nowEpochMs: Long,
    ) = 0

    override suspend fun undelete(rowId: Long) = 0
}

private fun entity(
    version: Int,
    sampleSize: Int = 250,
    json: String = V1_LEGACY_JSON,
    behaviorRules: String = "",
) = StyleFingerprintEntity(
    version = version,
    createdAtEpochMs = 1_700_000_000_000,
    sampleSize = sampleSize,
    partnerScopeId = null,
    fingerprintJson = json,
    behaviorRules = behaviorRules,
)

private fun corpus(
    version: Int,
    text: String,
    scenario: String,
) = CorpusSampleEntity(
    fingerprintVersion = version,
    text = text,
    scenario = scenario,
    createdAtEpochMs = 1_700_000_000_000,
)

class ProfileExportTest {
    @Test
    fun `exportLatest returns null when no profile exists`() =
        runTest {
            ProfileExport.exportLatest(StubStore(), StubCorpusStore()) shouldBe null
        }

    @Test
    fun `v2 export includes wrapper structure with behaviorRules and corpusSamples`() =
        runTest {
            val store = StubStore(listOf(entity(version = 5, behaviorRules = "## 我的风格\n- 短句")))
            val corpusStore =
                StubCorpusStore(
                    listOf(
                        corpus(version = 5, text = "在的", scenario = "日常问候"),
                        corpus(version = 5, text = "行吧", scenario = "拒绝"),
                    ),
                )
            val json = ProfileExport.exportLatest(store, corpusStore)!!
            json.shouldContain("\"fingerprint\"")
            json.shouldContain("\"behaviorRules\"")
            json.shouldContain("我的风格")
            json.shouldContain("\"corpusSamples\"")
            json.shouldContain("在的")
            json.shouldContain("日常问候")
        }

    @Test
    fun `v2 export-import round-trip preserves behaviorRules and corpus`() =
        runTest {
            val src = StubStore(listOf(entity(version = 3, behaviorRules = "## rules\n- a")))
            val srcCorpus =
                StubCorpusStore(
                    listOf(
                        corpus(version = 3, text = "句1", scenario = "日常问候"),
                        corpus(version = 3, text = "句2", scenario = "拒绝"),
                    ),
                )
            val exported = ProfileExport.exportLatest(src, srcCorpus)!!

            val dst = StubStore()
            val dstCorpus = StubCorpusStore()
            val result = ProfileExport.importJson(dst, dstCorpus, exported)
            result.shouldBeInstanceOf<ImportResult.Success>()
            result.isV1Legacy shouldBe false
            result.corpusSamplesImported shouldBe 2

            // behaviorRules preserved on the new fingerprint row
            dst.inserted.single().behaviorRules shouldContain "rules"
            // Corpus inserted under the NEW version assigned by destination store (1)
            dstCorpus.inserted shouldHaveSize 2
            dstCorpus.inserted.first().fingerprintVersion shouldBe result.newVersion
            dstCorpus.inserted.map { it.text }.toSet() shouldBe setOf("句1", "句2")
        }

    @Test
    fun `legacy v1 raw fingerprint JSON imports with empty behaviorRules`() =
        runTest {
            val store = StubStore()
            val corpusStore = StubCorpusStore()
            val result = ProfileExport.importJson(store, corpusStore, V1_LEGACY_JSON)
            result.shouldBeInstanceOf<ImportResult.Success>()
            result.isV1Legacy shouldBe true
            result.corpusSamplesImported shouldBe 0
            store.inserted.single().behaviorRules shouldBe ""
            corpusStore.inserted shouldHaveSize 0
        }

    @Test
    fun `import assigns nextVersion ignoring the version field in the file`() =
        runTest {
            val store = StubStore(listOf(entity(version = 5)))
            val result = ProfileExport.importJson(store, StubCorpusStore(), V1_LEGACY_JSON)
            result.shouldBeInstanceOf<ImportResult.Success>()
            result.newVersion shouldBe 6 // 5 + 1, not the file's 7
            store.inserted.size shouldBe 2
        }

    @Test
    fun `empty input maps to EmptyJson`() =
        runTest {
            ProfileExport.importJson(StubStore(), StubCorpusStore(), "   \n  ")
                .shouldBeInstanceOf<ImportResult.EmptyJson>()
        }

    @Test
    fun `malformed JSON maps to InvalidJson`() =
        runTest {
            ProfileExport.importJson(StubStore(), StubCorpusStore(), "{ not json }")
                .shouldBeInstanceOf<ImportResult.InvalidJson>()
        }

    @Test
    fun `parses-but-empty payload maps to NotAFingerprint`() =
        runTest {
            ProfileExport.importJson(StubStore(), StubCorpusStore(), "{}")
                .shouldBeInstanceOf<ImportResult.NotAFingerprint>()
        }

    @Test
    fun `looksLikeV2Wrapper detects wrapper vs raw`() {
        ProfileExport.looksLikeV2Wrapper("""{"fingerprint":{"linguistic":{}}}""") shouldBe true
        ProfileExport.looksLikeV2Wrapper("""{"linguistic":{}}""") shouldBe false
    }
}
