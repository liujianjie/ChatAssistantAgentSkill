package com.stylemirror.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stylemirror.core.data.db.StyleMirrorDatabase
import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CorpusSampleRepositoryTest {
    private lateinit var db: StyleMirrorDatabase
    private lateinit var repo: CorpusSampleRepository

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = StyleMirrorDatabase.createInMemory(ctx)
        repo = CorpusSampleRepository(db.corpusSampleDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insertAll returns row ids and findActive reads them back`() =
        runTest {
            val ids =
                repo.insertAll(
                    listOf(
                        sample(version = 1, scenario = "日常问候", text = "在的"),
                        sample(version = 1, scenario = "调侃", text = "你又来了"),
                    ),
                )
            ids shouldHaveSize 2

            val rows = repo.findActiveByVersion(1)
            rows shouldHaveSize 2
            rows.map { it.text }.toSet() shouldBe setOf("在的", "你又来了")
        }

    @Test
    fun `findActive filters by version`() =
        runTest {
            repo.insertAll(
                listOf(
                    sample(version = 1, scenario = "日常问候", text = "v1"),
                    sample(version = 2, scenario = "日常问候", text = "v2"),
                ),
            )

            repo.findActiveByVersion(1).single().text shouldBe "v1"
            repo.findActiveByVersion(2).single().text shouldBe "v2"
        }

    @Test
    fun `softDelete hides the row from findActive but findAll still returns it`() =
        runTest {
            val ids = repo.insertAll(listOf(sample(version = 1, scenario = "拒绝", text = "下次吧")))
            val rowId = ids.single()

            repo.softDelete(rowId, nowEpochMs = 1_700_000_000_000) shouldBe 1

            repo.findActiveByVersion(1).shouldHaveSize(0)
            // findAll keeps the soft-deleted row so export/import preserves user state.
            val all = repo.findAllByVersion(1)
            all shouldHaveSize 1
            all.single().deletedAtEpochMs.shouldNotBeNull()
        }

    @Test
    fun `softDelete on already-deleted row reports zero rows changed`() =
        runTest {
            val rowId = repo.insertAll(listOf(sample(version = 1, scenario = "x", text = "y"))).single()
            repo.softDelete(rowId, nowEpochMs = 1L) shouldBe 1
            repo.softDelete(rowId, nowEpochMs = 2L) shouldBe 0 // idempotent — already deleted
        }

    @Test
    fun `undelete restores a soft-deleted row`() =
        runTest {
            val rowId = repo.insertAll(listOf(sample(version = 1, scenario = "x", text = "y"))).single()
            repo.softDelete(rowId, nowEpochMs = 1L)
            repo.undelete(rowId) shouldBe 1

            val active = repo.findActiveByVersion(1).single()
            active.deletedAtEpochMs.shouldBeNull()
        }
}

private fun sample(
    version: Int,
    scenario: String,
    text: String,
    partnerScopeId: String? = null,
) = CorpusSampleEntity(
    fingerprintVersion = version,
    partnerScopeId = partnerScopeId,
    text = text,
    scenario = scenario,
    createdAtEpochMs = 1_700_000_000_000,
)
