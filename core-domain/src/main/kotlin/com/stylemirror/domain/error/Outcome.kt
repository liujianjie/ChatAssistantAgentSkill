package com.stylemirror.domain.error

/**
 * A two-channel result type used across UseCase / provider boundaries.
 *
 * Why not [kotlin.Result]? The stdlib variant locks the error channel to
 * [Throwable], which collapses our typed [DomainError] taxonomy and turns
 * exhaustive `when` into a runtime cast. [Outcome] keeps the error type open so
 * `Outcome<List<Candidate>, DomainError.LlmFailure>` is expressible without
 * subclassing `Throwable`.
 */
sealed interface Outcome<out T, out E> {
    data class Ok<out T>(val value: T) : Outcome<T, Nothing>

    data class Err<out E>(val error: E) : Outcome<Nothing, E>

    fun isOk(): Boolean = this is Ok

    fun isErr(): Boolean = this is Err

    fun valueOrNull(): T? = (this as? Ok)?.value

    fun errorOrNull(): E? = (this as? Err)?.error
}

fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(transform(value))
        is Outcome.Err -> this
    }

fun <T, E, F> Outcome<T, E>.mapError(transform: (E) -> F): Outcome<T, F> =
    when (this) {
        is Outcome.Ok -> this
        is Outcome.Err -> Outcome.Err(transform(error))
    }

fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> transform(value)
        is Outcome.Err -> this
    }

fun <T, E> Outcome<T, E>.getOrElse(fallback: (E) -> T): T =
    when (this) {
        is Outcome.Ok -> value
        is Outcome.Err -> fallback(error)
    }
