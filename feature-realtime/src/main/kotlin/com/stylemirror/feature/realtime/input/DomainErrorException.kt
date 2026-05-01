package com.stylemirror.feature.realtime.input

import com.stylemirror.domain.error.DomainError

/**
 * Carrier exception used by adapters that haven't been wired yet to surface
 * `DomainError.NotImplemented` through a [kotlinx.coroutines.flow.Flow] without
 * widening the [InputAdapter] return type to `Outcome<...>`.
 *
 * Catch and translate at the ViewModel boundary (T08) — never let it escape
 * into UI render paths.
 *
 * Scope intentionally local to this module: `core-domain.DomainError` is a
 * pure value; the *transport* (exception, sealed Outcome, etc.) belongs to
 * each consumer and is allowed to differ between modules.
 */
internal class DomainErrorException(
    val domainError: DomainError,
) : RuntimeException("DomainError surfaced from input adapter: $domainError")
