package com.stylemirror.infra.net.logging

import okhttp3.logging.HttpLoggingInterceptor

/**
 * OkHttp [HttpLoggingInterceptor.Logger] that redacts secrets from each log
 * line before it leaves the process.
 *
 * Two redaction strategies are applied (case-insensitive) in order, so a
 * single line containing both an HTTP header and a JSON body is sanitised in
 * full:
 *
 * 1. **HTTP header form** — `Authorization: …`, `X-Api-Key: …`,
 *    `Cookie: …`, `Set-Cookie: …`, plus any header whose name matches the
 *    secret-name allow-list. The full value is replaced by [REDACTED].
 * 2. **JSON form** — a string-typed JSON value whose key matches the
 *    secret-name allow-list (e.g. `"api_key": "…"`,
 *    `"refresh_token": "…"`) has its value replaced by [REDACTED]. The key
 *    name is preserved so failure logs still tell us *which* field held a
 *    secret.
 *
 * The interceptor only redacts what flows through OkHttp's logging pipeline —
 * the actual outbound request bytes are untouched, so servers still receive
 * the real Authorization header.
 *
 * SPEC §6.3 / global rule "禁止在对话中回显明文密钥": every log line we emit
 * is treated as potentially user-visible (logcat, bug reports, crash
 * uploads), so we redact eagerly rather than relying on caller hygiene.
 */
public class RedactingHttpLogger(
    private val delegate: (String) -> Unit = { line -> println(line) },
) : HttpLoggingInterceptor.Logger {
    override fun log(message: String) {
        delegate(redact(message))
    }

    public companion object {
        public const val REDACTED: String = "[REDACTED]"

        // Field names treated as secrets in BOTH header and JSON contexts.
        private const val SECRET_KEY_PATTERN =
            "(?:key|token|secret|password|passwd|authorization|cookie|credential)"

        // 1. Header form: `Name: value` at start of line (after optional
        //    OkHttp prefix). We only need to match names that match the
        //    secret-key pattern. Also covers explicit `Set-Cookie:` and
        //    `X-Api-Key:` because both contain "cookie"/"key".
        private val HEADER_REGEX =
            Regex(
                pattern = "(?im)^([\\w-]*$SECRET_KEY_PATTERN[\\w-]*)\\s*:\\s*.+$",
            )

        // 2. JSON string-value form: `"name": "value"` where name matches
        //    the secret-key pattern. We tolerate optional whitespace and
        //    escaped quotes inside the value but stop at the closing
        //    unescaped quote.
        private val JSON_REGEX =
            Regex(
                pattern =
                    "(?i)(\"[\\w-]*$SECRET_KEY_PATTERN[\\w-]*\"\\s*:\\s*\")" +
                        "(?:[^\"\\\\]|\\\\.)*" +
                        "(\")",
            )

        /**
         * Apply both redaction passes to [line]. Public for unit tests so we
         * can assert on the regex behaviour without instantiating an
         * interceptor.
         */
        public fun redact(line: String): String {
            val afterHeader =
                HEADER_REGEX.replace(line) { match ->
                    "${match.groupValues[1]}: $REDACTED"
                }
            return JSON_REGEX.replace(afterHeader) { match ->
                "${match.groupValues[1]}$REDACTED${match.groupValues[2]}"
            }
        }
    }
}
