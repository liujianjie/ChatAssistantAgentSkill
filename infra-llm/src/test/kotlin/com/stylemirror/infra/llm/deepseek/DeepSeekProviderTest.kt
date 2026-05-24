package com.stylemirror.infra.llm.deepseek

import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.LlmFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.infra.llm.testing.InMemorySecureKeyStore
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class DeepSeekProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var keyStore: InMemorySecureKeyStore

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        keyStore = InMemorySecureKeyStore(mapOf(DeepSeekProvider.DEFAULT_KEY_NAME to "sk-test"))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun newProvider(client: OkHttpClient = defaultClient()): DeepSeekProvider =
        DeepSeekProvider.create(
            keyStore = keyStore,
            client = client,
            baseUrl = server.url("/").toString(),
        )

    private fun defaultClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()

    @Test
    fun `200 returns parsed candidates with bearer auth attached`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "id": "abc",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "你好"}, "finish_reason": "stop"},
                            {"index": 1, "message": {"role": "assistant", "content": "Hi"},  "finish_reason": "stop"}
                          ],
                          "usage": {"prompt_tokens": 10, "completion_tokens": 8, "total_tokens": 18}
                        }
                        """.trimIndent(),
                    ),
            )
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "say hi", n = 2)

            val candidates = (outcome as Outcome.Ok).value
            candidates shouldHaveSize 2
            candidates[0].text shouldBe "你好"
            candidates[1].text shouldBe "Hi"
            candidates[0].tokens shouldBe 4 // 8 completion tokens / 2 candidates

            val recorded = server.takeRequest()
            recorded.path shouldBe "/chat/completions"
            recorded.getHeader("Authorization") shouldBe "Bearer sk-test"
            recorded.body.readUtf8().shouldContain("\"model\":\"deepseek-chat\"")
            // DeepSeek only supports n=1 — provider must always send 1 regardless of caller's n.
            recorded.body.readUtf8().shouldContain("\"n\":1")
        }

    @Test
    fun `single choice with multiple lines is split into N candidates`() =
        runTest {
            // DeepSeek's actual behavior: returns a single choice; prompt asks for N lines.
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "id": "abc",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "在的，怎么了\n刚到家\n稍等我看下"}, "finish_reason": "stop"}
                          ],
                          "usage": {"prompt_tokens": 20, "completion_tokens": 30, "total_tokens": 50}
                        }
                        """.trimIndent(),
                    ),
            )
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "x", n = 3)

            val candidates = (outcome as Outcome.Ok).value
            candidates shouldHaveSize 3
            candidates[0].text shouldBe "在的，怎么了"
            candidates[1].text shouldBe "刚到家"
            candidates[2].text shouldBe "稍等我看下"
            candidates[0].tokens shouldBe 10 // 30 / 3
        }

    @Test
    fun `numbered prefixes are stripped when splitting`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "id": "x",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "1. 在的\n2、好的\n3) 嗯嗯"}, "finish_reason": "stop"}
                          ]
                        }
                        """.trimIndent(),
                    ),
            )
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "x", n = 3)

            val candidates = (outcome as Outcome.Ok).value
            candidates shouldHaveSize 3
            candidates[0].text shouldBe "在的"
            candidates[1].text shouldBe "好的"
            candidates[2].text shouldBe "嗯嗯"
        }

    @Test
    fun `single line response yields one candidate without erroring`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "id": "x",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "好的"}, "finish_reason": "stop"}
                          ]
                        }
                        """.trimIndent(),
                    ),
            )
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "x", n = 3)

            val candidates = (outcome as Outcome.Ok).value
            candidates shouldHaveSize 1
            candidates[0].text shouldBe "好的"
        }

    @Test
    fun `400 maps to INVALID_RESPONSE with cause set so caller can show HTTP code`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad request"}"""))
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "x")

            val err = (outcome as Outcome.Err).error.shouldBeInstanceOf<DomainError.LlmFailure>()
            err.reason shouldBe LlmFailureReason.INVALID_RESPONSE
            err.cause shouldNotBe null
            // retrofit2.HttpException.message() = "HTTP 400 Bad Request"
            err.cause!!.message.orEmpty().shouldContain("400")
        }

    @Test
    fun `429 maps to RATE_LIMITED`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "x")

            val err = (outcome as Outcome.Err).error.shouldBeInstanceOf<DomainError.LlmFailure>()
            err.reason shouldBe LlmFailureReason.RATE_LIMITED
        }

    @Test
    fun `401 maps to AUTH`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "x")

            val err = (outcome as Outcome.Err).error.shouldBeInstanceOf<DomainError.LlmFailure>()
            err.reason shouldBe LlmFailureReason.AUTH
        }

    @Test
    fun `500 maps to SERVER_ERROR`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "x")

            val err = (outcome as Outcome.Err).error.shouldBeInstanceOf<DomainError.LlmFailure>()
            err.reason shouldBe LlmFailureReason.SERVER_ERROR
        }

    @Test
    fun `read timeout maps to TIMEOUT`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBodyDelay(5, TimeUnit.SECONDS)
                    .setBody("{}"),
            )
            // Aggressive 500ms read timeout — well under the body delay above.
            val tightClient =
                OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(500, TimeUnit.MILLISECONDS)
                    .writeTimeout(2, TimeUnit.SECONDS)
                    .build()
            val provider = newProvider(client = tightClient)

            val outcome = provider.generateCandidates(prompt = "x")

            val err = (outcome as Outcome.Err).error.shouldBeInstanceOf<DomainError.LlmFailure>()
            err.reason shouldBe LlmFailureReason.TIMEOUT
        }

    @Test
    fun `missing API key short-circuits to KEY_MISSING with no HTTP call`() =
        runTest {
            keyStore.remove(DeepSeekProvider.DEFAULT_KEY_NAME)
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "x")

            val err = (outcome as Outcome.Err).error.shouldBeInstanceOf<DomainError.LlmFailure>()
            err.reason shouldBe LlmFailureReason.KEY_MISSING
            // No request should have been queued.
            server.requestCount shouldBe 0
        }

    @Test
    fun `empty choices array maps to INVALID_RESPONSE`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"id":"x","choices":[]}"""),
            )
            val provider = newProvider()

            val outcome = provider.generateCandidates(prompt = "x")

            val err = (outcome as Outcome.Err).error.shouldBeInstanceOf<DomainError.LlmFailure>()
            err.reason shouldBe LlmFailureReason.INVALID_RESPONSE
        }
}
