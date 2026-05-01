package com.stylemirror.infra.net.logging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Unit tests for [RedactingHttpLogger]. These are the privacy red-line tests
 * referenced by SPEC §6.3 — if any of them fail, secrets can leak to logcat.
 *
 * The "fingerprint" we assert on (e.g. `sk-test-`) is a deliberate
 * **placeholder** that has never been a real key. We pick prefixes long
 * enough that "no leak" means "the redactor caught the entire value, not
 * just a suffix".
 */
class RedactingHttpLoggerTest : FunSpec({

    test("Authorization header value is replaced by [REDACTED]") {
        val line = "Authorization: Bearer sk-test-abcdef1234567890"
        val out = RedactingHttpLogger.redact(line)
        out shouldContain "Authorization"
        out shouldContain "[REDACTED]"
        out shouldNotContain "sk-test-"
        out shouldNotContain "Bearer"
    }

    test("X-Api-Key header value is replaced by [REDACTED]") {
        val line = "X-Api-Key: sk-test-abcdef1234567890"
        val out = RedactingHttpLogger.redact(line)
        out shouldContain "X-Api-Key"
        out shouldContain "[REDACTED]"
        out shouldNotContain "sk-test-"
    }

    test("Cookie header value is replaced") {
        val line = "Cookie: session=abcdef1234567890; theme=dark"
        val out = RedactingHttpLogger.redact(line)
        out shouldContain "[REDACTED]"
        out shouldNotContain "abcdef1234"
    }

    test("Set-Cookie header value is replaced") {
        val line = "Set-Cookie: SID=zzz12345abcde; Path=/"
        val out = RedactingHttpLogger.redact(line)
        out shouldContain "[REDACTED]"
        out shouldNotContain "zzz12345"
    }

    test("JSON api_key value is redacted, key name preserved") {
        val line = """{"api_key":"sk-test-xxxxxxxxxxxx","model":"x"}"""
        val out = RedactingHttpLogger.redact(line)
        out shouldContain "\"api_key\""
        out shouldContain "[REDACTED]"
        out shouldNotContain "sk-test-"
        // Non-secret fields untouched.
        out shouldContain "\"model\""
        out shouldContain "\"x\""
    }

    test("JSON access_token / refresh_token / secret / password redacted") {
        val line =
            """{"access_token":"a-aaaaaaaaaa","refresh_token":"r-yyyyyyyyyy",""" +
                """"secret":"s-zzzzzzzzzz","password":"p-pppppppppp"}"""
        val out = RedactingHttpLogger.redact(line)
        out shouldNotContain "a-aaaaaaaa"
        out shouldNotContain "r-yyyyyyyy"
        out shouldNotContain "s-zzzzzzzz"
        out shouldNotContain "p-pppppppp"
        out shouldContain "[REDACTED]"
    }

    test("matching is case-insensitive on field name") {
        val line = """AUTHORIZATION: Bearer sk-test-uppercase12345"""
        val out = RedactingHttpLogger.redact(line)
        out shouldNotContain "sk-test-uppercase"
        out shouldContain "[REDACTED]"
    }

    test("non-secret JSON fields untouched") {
        val line = """{"username":"alice","model":"deepseek-chat"}"""
        val out = RedactingHttpLogger.redact(line)
        out shouldBe line
    }

    test("instance routes redacted line to delegate") {
        val captured = mutableListOf<String>()
        val logger = RedactingHttpLogger(delegate = { captured += it })
        logger.log("Authorization: Bearer sk-test-1234567890abcdef")
        captured.size shouldBe 1
        captured[0] shouldContain "[REDACTED]"
        captured[0] shouldNotContain "sk-test-"
    }
})
