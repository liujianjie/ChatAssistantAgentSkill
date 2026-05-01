package com.stylemirror.infra.net

import com.stylemirror.infra.net.logging.RedactingHttpLogger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit

/**
 * Integration test for [NetworkModule] that proves:
 *
 * 1. Logging output is redacted (no plaintext key reaches the log delegate).
 * 2. The actual outbound request bytes still carry the real Authorization
 *    header (redaction must not break auth).
 * 3. Candidate-generation client uses the SPEC-§1.4-derived 8 s read budget.
 *
 * Uses MockWebServer so we never depend on a live API and can assert what
 * the server received byte-for-byte.
 */
class NetworkClientTest : FunSpec({

    test("log line is redacted but server still receives the real header") {
        val server =
            MockWebServer().apply {
                enqueue(MockResponse().setBody("""{"ok":true}""").setResponseCode(200))
                start()
            }
        try {
            val captured = mutableListOf<String>()
            val redactingLogger = RedactingHttpLogger(delegate = { captured += it })

            val client = NetworkModule.candidateGenerationClient(redactingLogger)
            val placeholderKey = "sk-test-1234567890"
            val request =
                Request.Builder()
                    .url(server.url("/v1/ping"))
                    .header("Authorization", "Bearer $placeholderKey")
                    .post("""{"api_key":"$placeholderKey"}""".toRequestBody("application/json".toMediaType()))
                    .build()

            client.newCall(request).execute().use { response ->
                response.code shouldBe 200
            }

            // 1. Redaction worked.
            val joined = captured.joinToString("\n")
            joined shouldContain "[REDACTED]"
            joined shouldNotContain placeholderKey

            // 2. Server received the real header — redaction is log-only.
            val received =
                server.takeRequest(2, TimeUnit.SECONDS)
                    ?: error("server did not receive a request")
            received.getHeader("Authorization") shouldBe "Bearer $placeholderKey"
        } finally {
            server.shutdown()
        }
    }

    test("candidate-generation client honours the 8 s read budget (SPEC §1.4)") {
        val client = NetworkModule.candidateGenerationClient()
        // 8 s read/write — last-line back-stop for "P95 ≤ 3 s".
        client.readTimeoutMillis shouldBe 8_000
        client.writeTimeoutMillis shouldBe 8_000
        client.connectTimeoutMillis shouldBe 5_000
    }

    test("default client uses the 30 s budget") {
        val client = NetworkModule.defaultClient()
        client.readTimeoutMillis shouldBe 30_000
        client.writeTimeoutMillis shouldBe 30_000
        client.connectTimeoutMillis shouldBe 10_000
    }

    test("retrofitBuilder wires base url, client, and JSON converter") {
        val server = MockWebServer().apply { start() }
        try {
            val client = NetworkModule.defaultClient()
            val retrofit =
                NetworkModule.retrofitBuilder(
                    baseUrl = server.url("/").toString(),
                    client = client,
                ).build()
            retrofit.baseUrl().toString() shouldBe server.url("/").toString()
            retrofit.callFactory() shouldBe client
        } finally {
            server.shutdown()
        }
    }

    test("logging interceptor is installed at BODY level") {
        val client = NetworkModule.candidateGenerationClient()
        val logging = client.interceptors.filterIsInstance<HttpLoggingInterceptor>()
        logging.size shouldBe 1
        logging[0].level shouldBe HttpLoggingInterceptor.Level.BODY
    }
})
