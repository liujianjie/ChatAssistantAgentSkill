package com.stylemirror.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stylemirror.core.data.db.StyleMirrorDatabase
import com.stylemirror.domain.feedback.CandidateId
import com.stylemirror.domain.feedback.DiscardReason
import com.stylemirror.domain.feedback.FeedbackSignal
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class FeedbackRepositoryTest {
    private lateinit var db: StyleMirrorDatabase
    private lateinit var repo: FeedbackRepository

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = StyleMirrorDatabase.createInMemory(ctx)
        repo = FeedbackRepository(db.feedbackSignalDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `record and retrieve Adopt signal`() =
        runTest {
            val signal =
                FeedbackSignal.Adopt(
                    candidateId = CandidateId("c1"),
                    fingerprintVersion = 1,
                    createdAt = Instant.parse("2026-05-01T10:00:00Z"),
                )
            repo.record("sig-1", signal)

            val all = repo.findAll()
            all shouldHaveSize 1
            all.first().shouldBeInstanceOf<FeedbackSignal.Adopt>()
            (all.first() as FeedbackSignal.Adopt).candidateId shouldBe CandidateId("c1")
        }

    @Test
    fun `record and retrieve Modify signal preserves edited content`() =
        runTest {
            val signal =
                FeedbackSignal.Modify(
                    candidateId = CandidateId("c2"),
                    fingerprintVersion = 1,
                    createdAt = Instant.parse("2026-05-01T10:01:00Z"),
                    editedContent = "用户改写后的内容",
                )
            repo.record("sig-2", signal)

            val result = repo.findAll().first() as FeedbackSignal.Modify
            result.editedContent shouldBe "用户改写后的内容"
        }

    @Test
    fun `record and retrieve Discard signal preserves reason`() =
        runTest {
            val signal =
                FeedbackSignal.Discard(
                    candidateId = CandidateId("c3"),
                    fingerprintVersion = 1,
                    createdAt = Instant.parse("2026-05-01T10:02:00Z"),
                    reason = DiscardReason.TOO_LONG,
                )
            repo.record("sig-3", signal)

            val result = repo.findAll().first() as FeedbackSignal.Discard
            result.reason shouldBe DiscardReason.TOO_LONG
        }

    @Test
    fun `count returns total number of recorded signals`() =
        runTest {
            val ts = Instant.parse("2026-05-01T10:00:00Z")
            repo.record("a", FeedbackSignal.Adopt(CandidateId("c1"), 1, ts))
            repo.record("b", FeedbackSignal.Adopt(CandidateId("c2"), 1, ts))
            repo.record("c", FeedbackSignal.Adopt(CandidateId("c3"), 1, ts))

            repo.count() shouldBe 3
        }

    @Test
    fun `findByFingerprintVersion filters by version`() =
        runTest {
            val ts = Instant.parse("2026-05-01T10:00:00Z")
            repo.record("v1", FeedbackSignal.Adopt(CandidateId("c1"), 1, ts))
            repo.record("v2", FeedbackSignal.Adopt(CandidateId("c2"), 2, ts))
            repo.record("v3", FeedbackSignal.Adopt(CandidateId("c3"), 1, ts))

            repo.findByFingerprintVersion(1) shouldHaveSize 2
            repo.findByFingerprintVersion(2) shouldHaveSize 1
        }

    @Test
    fun `duplicate id is silently ignored (IGNORE conflict strategy)`() =
        runTest {
            val ts = Instant.parse("2026-05-01T10:00:00Z")
            val signal = FeedbackSignal.Adopt(CandidateId("c1"), 1, ts)
            repo.record("dup", signal)
            repo.record("dup", signal) // second insert should be ignored

            repo.count() shouldBe 1
        }
}
