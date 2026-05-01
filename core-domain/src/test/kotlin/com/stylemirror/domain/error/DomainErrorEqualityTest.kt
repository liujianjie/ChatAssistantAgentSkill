package com.stylemirror.domain.error

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Exercises the auto-generated members of every DomainError data class so the
 * coverage gate doesn't get held hostage by Kotlin's synthetic copy/equals.
 *
 * If a new variant is added with payload, mirror an entry here — gaps will
 * show up immediately as a coverage drop on the package.
 */
class DomainErrorEqualityTest : StringSpec({

    "LlmFailure equality, copy, toString" {
        val a = DomainError.LlmFailure(LlmFailureReason.TIMEOUT)
        val b = DomainError.LlmFailure(LlmFailureReason.TIMEOUT)
        val c = a.copy(reason = LlmFailureReason.AUTH)
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
        a shouldNotBe c
        a.toString().contains("TIMEOUT") shouldBe true
    }

    "OcrFailure equality, copy" {
        val a = DomainError.OcrFailure(OcrFailureReason.NO_TEXT_DETECTED)
        a.copy(reason = OcrFailureReason.IMAGE_UNREADABLE).reason shouldBe OcrFailureReason.IMAGE_UNREADABLE
        a shouldBe DomainError.OcrFailure(OcrFailureReason.NO_TEXT_DETECTED)
    }

    "ImportFailure equality, copy" {
        val a = DomainError.ImportFailure(ImportFailureReason.PARSE_ERROR)
        a.copy(reason = ImportFailureReason.TOO_LARGE).reason shouldBe ImportFailureReason.TOO_LARGE
        a shouldBe DomainError.ImportFailure(ImportFailureReason.PARSE_ERROR)
    }

    "InsufficientProfile equality, copy, hashCode" {
        val a = DomainError.InsufficientProfile(collectedSamples = 5, required = 10)
        val b = DomainError.InsufficientProfile(collectedSamples = 5, required = 10)
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
        a.copy(collectedSamples = 9).collectedSamples shouldBe 9
    }

    "QuotaExceeded equality, copy" {
        val a = DomainError.QuotaExceeded(QuotaLimit.DAILY_LLM_CALLS)
        a.copy(limit = QuotaLimit.MONTHLY_TOKEN_BUDGET).limit shouldBe QuotaLimit.MONTHLY_TOKEN_BUDGET
        a shouldBe DomainError.QuotaExceeded(QuotaLimit.DAILY_LLM_CALLS)
    }

    "QuotaLimit enumerates the documented buckets" {
        QuotaLimit.values().toSet() shouldBe
            setOf(
                QuotaLimit.DAILY_LLM_CALLS,
                QuotaLimit.MONTHLY_TOKEN_BUDGET,
                QuotaLimit.CONCURRENT_IMPORTS,
            )
    }
})
