package com.stylemirror.domain.error

/**
 * Domain-level error taxonomy. Every UseCase / interface that can fail returns
 * `Outcome<T, DomainError>` (or a narrower subtype). System exceptions get
 * mapped at the outermost ViewModel boundary — see SPEC §4.4.
 *
 * Adding a new variant is a breaking change on purpose: `when` branches in
 * UseCase consumers must be exhaustive so error paths can't be silently
 * dropped.
 */
sealed class DomainError(open val cause: Throwable? = null) {
    /** LLM provider call failed — see [LlmFailureReason] for the bucket. */
    data class LlmFailure(
        val reason: LlmFailureReason,
        override val cause: Throwable? = null,
    ) : DomainError(cause)

    /** OCR provider returned no usable text or threw. */
    data class OcrFailure(
        val reason: OcrFailureReason,
        override val cause: Throwable? = null,
    ) : DomainError(cause)

    /** Import pipeline rejected the input (parse / format / size). */
    data class ImportFailure(
        val reason: ImportFailureReason,
        override val cause: Throwable? = null,
    ) : DomainError(cause)

    /**
     * Not enough of the user's own messages to produce a meaningful fingerprint.
     * Surfaces in onboarding when a user supplies a tiny seed corpus.
     */
    data class InsufficientProfile(val collectedSamples: Int, val required: Int) : DomainError() {
        init {
            require(collectedSamples >= 0) { "collectedSamples must be >= 0" }
            require(required > 0) { "required must be > 0" }
        }
    }

    /** User has hit a configured per-day or per-month quota. */
    data class QuotaExceeded(val limit: QuotaLimit) : DomainError()

    /** A provider hook was called but no implementation is wired yet (e.g. P1 stubs). */
    data object NotImplemented : DomainError()
}

enum class LlmFailureReason { TIMEOUT, RATE_LIMITED, AUTH, SERVER_ERROR, INVALID_RESPONSE, KEY_MISSING }

enum class OcrFailureReason { NO_TEXT_DETECTED, IMAGE_UNREADABLE, PROVIDER_ERROR }

enum class ImportFailureReason { PARSE_ERROR, EMPTY_INPUT, UNSUPPORTED_FORMAT, TOO_LARGE }

enum class QuotaLimit { DAILY_LLM_CALLS, MONTHLY_TOKEN_BUDGET, CONCURRENT_IMPORTS }
