package com.stylemirror.feature.realtime.retrieval

import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import com.stylemirror.core.data.repository.CorpusSampleStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.emptyFlow

private fun sample(
    text: String,
    scenario: String,
    version: Int = 1,
) = CorpusSampleEntity(
    fingerprintVersion = version,
    text = text,
    scenario = scenario,
    createdAtEpochMs = 0L,
)

private class FakeCorpusStore(private val samples: List<CorpusSampleEntity>) : CorpusSampleStore {
    override suspend fun insertAll(samples: List<CorpusSampleEntity>) = samples.indices.map { it.toLong() }

    override suspend fun findActiveByVersion(version: Int) =
        samples.filter { it.fingerprintVersion == version && it.deletedAtEpochMs == null }

    override suspend fun findAllByVersion(version: Int) = samples.filter { it.fingerprintVersion == version }

    override fun observeActiveByVersion(version: Int) = emptyFlow<List<CorpusSampleEntity>>()

    override suspend fun softDelete(
        rowId: Long,
        nowEpochMs: Long,
    ) = 0

    override suspend fun undelete(rowId: Long) = 0
}

class CorpusRetrieverTest : StringSpec({

    "empty corpus returns empty list" {
        val r = CorpusRetriever(FakeCorpusStore(emptyList()))
        r.retrieve(fingerprintVersion = 1, theirRecentMessages = listOf("你好")).shouldBeEmpty()
    }

    "scenario guess promotes matching-scenario samples to top" {
        val corpus =
            listOf(
                sample("行吧，下次再约", scenario = "拒绝"),
                sample("好的没问题", scenario = "日常问候"),
                sample("没事啦，下次注意就好", scenario = "安慰"),
                sample("哈哈哈你太逗了", scenario = "调侃"),
            )
        val r = CorpusRetriever(FakeCorpusStore(corpus), topN = 2)

        // Their message with apology trigger → "安慰" should rise
        val out = r.retrieve(1, listOf("对不起，刚才忙忘了"))
        out.shouldHaveSize(2)
        out.first().scenario shouldBe "安慰"
    }

    "bigram overlap breaks ties when scenario doesn't match" {
        val corpus =
            listOf(
                sample("我先睡了", scenario = "冷处理"),
                sample("我去吃饭啦", scenario = "其他"),
                sample("好的", scenario = "其他"),
            )
        val r = CorpusRetriever(FakeCorpusStore(corpus), topN = 1)

        // Their message contains "吃饭" → bigram "吃饭" overlaps with "我去吃饭啦"
        val out = r.retrieve(1, listOf("一会一起吃饭吗"))
        out.single().text shouldBe "我去吃饭啦"
    }

    "topN caps the result size" {
        val corpus = (1..20).map { sample("text$it", scenario = "其他") }
        val r = CorpusRetriever(FakeCorpusStore(corpus), topN = 5)
        r.retrieve(1, listOf("hi")).shouldHaveSize(5)
    }

    "soft-deleted samples are excluded by store contract" {
        val active = sample("active", scenario = "日常问候")
        val deleted =
            sample("deleted", scenario = "日常问候")
                .copy(deletedAtEpochMs = 1_000L)
        val r = CorpusRetriever(FakeCorpusStore(listOf(active, deleted)))

        // FakeCorpusStore.findActiveByVersion already filters deletedAt — Retriever
        // simply trusts the store to apply the filter.
        val out = r.retrieve(1, listOf("你好"))
        out.shouldHaveSize(1)
        out.single().text shouldBe "active"
    }

    "guessScenario detects greeting patterns" {
        val r = CorpusRetriever(FakeCorpusStore(emptyList()))
        r.guessScenario("在吗") shouldBe "日常问候"
        r.guessScenario("你好啊") shouldBe "日常问候"
    }

    "guessScenario detects apology patterns" {
        val r = CorpusRetriever(FakeCorpusStore(emptyList()))
        r.guessScenario("不好意思迟到了") shouldBe "安慰"
    }

    "guessScenario returns null for non-matching" {
        val r = CorpusRetriever(FakeCorpusStore(emptyList()))
        r.guessScenario("普通的一段话啦") shouldBe null
    }

    "bigramTokens covers all consecutive 2-char windows" {
        val r = CorpusRetriever(FakeCorpusStore(emptyList()))
        r.bigramTokens("我去吃饭") shouldContain "我去"
        r.bigramTokens("我去吃饭") shouldContain "去吃"
        r.bigramTokens("我去吃饭") shouldContain "吃饭"
    }
})
