package com.stylemirror.core.data.profiling

import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import com.stylemirror.core.data.repository.StyleFingerprintStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val SAMPLE_JSON = """
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

private fun entity(
    version: Int,
    sampleSize: Int = 250,
    json: String = SAMPLE_JSON,
) = StyleFingerprintEntity(
    version = version,
    createdAtEpochMs = 1_700_000_000_000,
    sampleSize = sampleSize,
    partnerScopeId = null,
    fingerprintJson = json,
)

class ProfileExportTest {
    @Test
    fun `exportLatest returns null when no profile exists`() =
        runTest {
            val store = StubStore()
            ProfileExport.exportLatest(store) shouldBe null
        }

    @Test
    fun `exportLatest returns JSON of the highest-version row`() =
        runTest {
            val store = StubStore(listOf(entity(version = 5), entity(version = 7)))
            val json = ProfileExport.exportLatest(store)
            json!!.shouldContain("\"sampleSize\"")
            json.shouldContain("250")
            json.shouldContain("\"formality\"")
            json.shouldContain("CASUAL")
        }

    @Test
    fun `import then export round-trips fingerprint values`() =
        runTest {
            val store = StubStore()
            val result = ProfileExport.importJson(store, SAMPLE_JSON)
            result.shouldBeInstanceOf<ImportResult.Success>()
            result.newVersion shouldBe 1 // empty store → nextVersion 1, ignores incoming "version": 7
            result.sampleSize shouldBe 250

            val exported = ProfileExport.exportLatest(store)!!
            // sampleSize / formality / emotional preserved
            exported.shouldContain("250")
            exported.shouldContain("CASUAL")
            exported.shouldContain("BALANCED")
            // The exported version was rewritten to the DB-assigned value (1), not the file's 7.
            exported.shouldContain("\"version\": 1")
        }

    @Test
    fun `import assigns nextVersion ignoring the version field in the file`() =
        runTest {
            val store = StubStore(listOf(entity(version = 5)))
            val result = ProfileExport.importJson(store, SAMPLE_JSON)
            result.shouldBeInstanceOf<ImportResult.Success>()
            result.newVersion shouldBe 6 // 5 + 1, NOT the 7 baked into the file
            store.inserted.size shouldBe 2 // old version 5 retained — caller never overwrites history
        }

    @Test
    fun `empty input maps to EmptyJson`() =
        runTest {
            ProfileExport.importJson(StubStore(), "   \n  ").shouldBeInstanceOf<ImportResult.EmptyJson>()
        }

    @Test
    fun `malformed JSON maps to InvalidJson`() =
        runTest {
            val res = ProfileExport.importJson(StubStore(), "{ this is not json }")
            res.shouldBeInstanceOf<ImportResult.InvalidJson>()
        }

    @Test
    fun `parses-but-empty payload maps to NotAFingerprint`() =
        runTest {
            // {} parses fine because all FpDto fields have defaults; sampleSize defaults to 0.
            val res = ProfileExport.importJson(StubStore(), "{}")
            res.shouldBeInstanceOf<ImportResult.NotAFingerprint>()
        }
}
