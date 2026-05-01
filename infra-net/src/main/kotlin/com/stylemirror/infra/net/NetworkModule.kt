package com.stylemirror.infra.net

import com.stylemirror.infra.net.logging.RedactingHttpLogger
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factories for the two [OkHttpClient] flavours every infra-* module shares.
 *
 * - [candidateGenerationClient] — tight budget (8 s read/write, 5 s connect)
 *   matching SPEC §1.4's "P95 ≤ 3 s for 3 candidates"; we cut the connection
 *   well before the user perceives a stall.
 * - [defaultClient] — generous budget (30 s) for non-interactive paths
 *   (sync, telemetry, fingerprint upload).
 *
 * Both clients install a single [HttpLoggingInterceptor] backed by
 * [RedactingHttpLogger] so secrets never reach logcat / crash dumps.
 *
 * Provider-specific Retrofit services (DeepSeek, Claude, Qwen) are *not*
 * defined here — that's T05's job. T04 ships the transport only.
 */
public object NetworkModule {
    private const val CANDIDATE_CONNECT_TIMEOUT_MS = 5_000L
    private const val CANDIDATE_READ_WRITE_TIMEOUT_MS = 8_000L
    private const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
    private const val DEFAULT_READ_WRITE_TIMEOUT_MS = 30_000L

    /** JSON payloads from third-party APIs grow new fields constantly; we
     *  refuse to break the app over an unknown field. */
    public val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
        }

    /**
     * Tight-budget client used by candidate generation. The 8 s read/write
     * cap is the back-stop for SPEC §1.4 latency; the timeout here is the
     * last line of defence even if a provider's own SDK lacks one.
     */
    public fun candidateGenerationClient(logger: HttpLoggingInterceptor.Logger = RedactingHttpLogger()): OkHttpClient =
        baseBuilder(logger)
            .connectTimeout(CANDIDATE_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(CANDIDATE_READ_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(CANDIDATE_READ_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

    /**
     * Default client for non-interactive traffic. Use this for any path
     * where 8 s would be too aggressive (background sync, large uploads).
     */
    public fun defaultClient(logger: HttpLoggingInterceptor.Logger = RedactingHttpLogger()): OkHttpClient =
        baseBuilder(logger)
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(DEFAULT_READ_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(DEFAULT_READ_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

    /**
     * Retrofit builder pre-wired with the kotlinx.serialization converter and
     * the supplied [client]. Callers append `baseUrl(...)` /
     * `addCallAdapterFactory(...)` as needed.
     */
    public fun retrofitBuilder(
        baseUrl: String,
        client: OkHttpClient,
    ): Retrofit.Builder =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))

    private fun baseBuilder(logger: HttpLoggingInterceptor.Logger): OkHttpClient.Builder {
        val logging =
            HttpLoggingInterceptor(logger).apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        return OkHttpClient.Builder().addInterceptor(logging)
    }
}
