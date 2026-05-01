package com.stylemirror.domain.conversation

/**
 * Stable identifier for a single message within a conversation. Wrapped in a
 * value class so call sites can't accidentally swap a message id for any other
 * String at compile time.
 */
@JvmInline
value class MessageId(val value: String) {
    init {
        require(value.isNotBlank()) { "MessageId must not be blank" }
    }
}

/**
 * Stable identifier for a conversation partner — the "other side" of a 1:1
 * thread or a group conversation handle. Domain layer never assumes this maps
 * to a specific platform identity (WeChat / Soul / etc.); persistence and
 * platform adapters own that translation.
 */
@JvmInline
value class PartnerId(val value: String) {
    init {
        require(value.isNotBlank()) { "PartnerId must not be blank" }
    }
}
