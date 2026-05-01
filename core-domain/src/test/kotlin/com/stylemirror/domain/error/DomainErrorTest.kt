package com.stylemirror.domain.error

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DomainErrorTest : StringSpec({

    "LlmFailure carries reason and optional cause" {
        val cause = RuntimeException("boom")
        val err = DomainError.LlmFailure(LlmFailureReason.TIMEOUT, cause)
        err.reason shouldBe LlmFailureReason.TIMEOUT
        err.cause shouldBe cause
    }

    "InsufficientProfile rejects negative collected and zero/negative required" {
        shouldThrow<IllegalArgumentException> {
            DomainError.InsufficientProfile(collectedSamples = -1, required = 100)
        }
        shouldThrow<IllegalArgumentException> {
            DomainError.InsufficientProfile(collectedSamples = 10, required = 0)
        }
        shouldThrow<IllegalArgumentException> {
            DomainError.InsufficientProfile(collectedSamples = 10, required = -1)
        }
    }

    "InsufficientProfile happy path" {
        val err = DomainError.InsufficientProfile(collectedSamples = 5, required = 50)
        err.collectedSamples shouldBe 5
        err.required shouldBe 50
    }

    "QuotaExceeded surfaces the limit kind" {
        DomainError.QuotaExceeded(QuotaLimit.DAILY_LLM_CALLS).limit shouldBe QuotaLimit.DAILY_LLM_CALLS
    }

    "NotImplemented is a singleton" {
        (DomainError.NotImplemented === DomainError.NotImplemented) shouldBe true
    }

    "exhaustive when on DomainError compiles" {
        val err: DomainError = DomainError.NotImplemented
        val label =
            when (err) {
                is DomainError.LlmFailure -> "llm"
                is DomainError.OcrFailure -> "ocr"
                is DomainError.ImportFailure -> "import"
                is DomainError.InsufficientProfile -> "insufficient"
                is DomainError.QuotaExceeded -> "quota"
                DomainError.NotImplemented -> "todo"
            }
        label shouldBe "todo"
    }

    "every failure-reason enum has the documented buckets" {
        LlmFailureReason.values().toSet() shouldBe
            setOf(
                LlmFailureReason.TIMEOUT,
                LlmFailureReason.RATE_LIMITED,
                LlmFailureReason.AUTH,
                LlmFailureReason.SERVER_ERROR,
                LlmFailureReason.INVALID_RESPONSE,
                LlmFailureReason.KEY_MISSING,
            )
        OcrFailureReason.values().toSet() shouldBe
            setOf(
                OcrFailureReason.NO_TEXT_DETECTED,
                OcrFailureReason.IMAGE_UNREADABLE,
                OcrFailureReason.PROVIDER_ERROR,
            )
        ImportFailureReason.values().toSet() shouldBe
            setOf(
                ImportFailureReason.PARSE_ERROR,
                ImportFailureReason.EMPTY_INPUT,
                ImportFailureReason.UNSUPPORTED_FORMAT,
                ImportFailureReason.TOO_LARGE,
            )
    }

    "OcrFailure variant carries reason" {
        val err: DomainError = DomainError.OcrFailure(OcrFailureReason.NO_TEXT_DETECTED)
        err.shouldBeInstanceOf<DomainError.OcrFailure>()
        err.reason shouldBe OcrFailureReason.NO_TEXT_DETECTED
    }

    "ImportFailure variant carries reason" {
        val err: DomainError = DomainError.ImportFailure(ImportFailureReason.PARSE_ERROR)
        err.shouldBeInstanceOf<DomainError.ImportFailure>()
        err.reason shouldBe ImportFailureReason.PARSE_ERROR
    }
})
