package com.stylemirror.domain.security

/**
 * Storage for high-sensitivity secrets — primarily LLM provider API keys, but
 * any short string that must survive process restart while never reaching
 * plaintext on disk, in logs, or in version control.
 *
 * Backs the SPEC privacy red-line:
 *   "API Key 走 EncryptedSharedPreferences；不进 settings.json/log/git".
 *
 * Failure semantics:
 *  - [get] returns `null` for an absent name; that is NOT an error.
 *  - [put] / [remove] throw on infrastructure failure (e.g. the underlying
 *    keystore is wedged, hardware crypto provider is missing). Such failures
 *    are unrecoverable for the user; the ViewModel layer maps them to a
 *    generic "secret store unavailable" error before reaching the UI.
 *
 * Implementations MUST NOT log values, return them in error messages, or
 * surface them in toString(). The interface is named-keyed (not blob-keyed)
 * so callers can't accidentally store binary data that happens to render.
 */
interface SecureKeyStore {
    suspend fun get(name: String): String?

    suspend fun put(
        name: String,
        value: String,
    )

    suspend fun remove(name: String)

    /**
     * Wipe every entry. Reserved for the SPEC §4.5 "wipe my data" / sign-out
     * flow — destructive, no confirmation at the storage layer; the UI is
     * responsible for the user prompt.
     */
    suspend fun clearAll()
}
