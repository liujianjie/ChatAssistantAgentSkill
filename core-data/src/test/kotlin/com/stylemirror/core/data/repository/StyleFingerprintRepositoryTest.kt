package com.stylemirror.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stylemirror.core.data.db.StyleMirrorDatabase
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
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
class StyleFingerprintRepositoryTest {
    private lateinit var db: StyleMirrorDatabase
    private lateinit var repo: StyleFingerprintRepository

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = StyleMirrorDatabase.createInMemory(ctx)
        repo = StyleFingerprintRepository(db.styleFingerprintDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `findLatest returns null when table is empty`() =
        runTest {
            repo.findLatest().shouldBeNull()
        }

    @Test
    fun `insert and findLatest returns highest version`() =
        runTest {
            repo.insert(fp(version = 1))
            repo.insert(fp(version = 2))
            repo.insert(fp(version = 3))

            repo.findLatest().shouldNotBeNull().version shouldBe 3
        }

    @Test
    fun `findByVersion retrieves the correct record`() =
        runTest {
            repo.insert(fp(version = 1))
            repo.insert(fp(version = 2))

            repo.findByVersion(1).shouldNotBeNull().version shouldBe 1
            repo.findByVersion(99).shouldBeNull()
        }

    @Test
    fun `nextVersion is 1 when table is empty`() =
        runTest {
            repo.nextVersion() shouldBe 1
        }

    @Test
    fun `nextVersion increments past existing max`() =
        runTest {
            repo.insert(fp(version = 5))
            repo.nextVersion() shouldBe 6
        }
}

private fun fp(version: Int) =
    StyleFingerprintEntity(
        version = version,
        createdAtEpochMs = 1_000_000L,
        sampleSize = 50,
        partnerScopeId = null,
        fingerprintJson = """{"stub":true}""",
    )
