package com.stylemirror.infra.llm.deepseek

import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.domain.error.DomainError
import com.stylemirror.domain.error.LlmFailureReason
import com.stylemirror.domain.error.Outcome
import com.stylemirror.domain.security.SecureKeyStore
import com.stylemirror.infra.llm.LLMProvider
import com.stylemirror.infra.net.NetworkModule
import okhttp3.OkHttpClient
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Production [LLMProvider] backed by DeepSeek's chat-completions endpoint.
 *
 * The API key lives in [SecureKeyStore] under [keyName] (default
 * [DEFAULT_KEY_NAME], namespaced so future Claude / Qwen keys stay tidy).
 * It is fetched on every call rather than cached on the provider so that:
 *  - rotation takes effect immediately (the next candidate request picks up
 *    the new value),
 *  - the secret never sits in heap longer than the call's stack frame.
 *
 * HTTP/transport errors map to typed [DomainError.LlmFailure] reasons:
 * - 401 / 403       → AUTH
 * - 429             → RATE_LIMITED
 * - 5xx             → SERVER_ERROR
 * - read/write timeout → TIMEOUT
 * - other [IOException] → SERVER_ERROR (network blip, DNS, etc.)
 * - empty `choices` → INVALID_RESPONSE (provider answered, but unusable)
 * - missing API key → KEY_MISSING (no HTTP call attempted)
 *
 * Exceptions never escape the provider — every failure exits via
 * [Outcome.Err], so callers can `when` exhaustively.
 *
 * @param api Retrofit binding for the chat-completions endpoint. Production
 *   code uses [create]; tests inject a MockWebServer-backed instance.
 * @param keyStore Storage from which the bearer token is read on each call.
 * @param model DeepSeek model id, defaulting to the cheapest production
 *   chat model. Reasoner / coder models can be swapped without touching
 *   call sites.
 * @param keyName Logical name under which the API key is stored.
 */
class DeepSeekProvider internal constructor(
    private val api: DeepSeekApi,
    private val keyStore: SecureKeyStore,
    private val model: String = DEFAULT_MODEL,
    private val keyName: String = DEFAULT_KEY_NAME,
) : LLMProvider {
    override suspend fun generateCandidates(
        prompt: String,
        maxTokens: Int,
        n: Int,
    ): Outcome<List<Candidate>, DomainError> {
        val apiKey =
            keyStore.get(keyName)
                ?: return Outcome.Err(DomainError.LlmFailure(reason = LlmFailureReason.KEY_MISSING))

        val request =
            DeepSeekChatRequest(
                model = model,
                messages = listOf(DeepSeekMessage(role = "user", content = prompt)),
                maxTokens = maxTokens,
                n = n,
            )

        return try {
            val response =
                api.chatCompletions(
                    authorization = "Bearer $apiKey",
                    request = request,
                )
            mapResponse(response)
        } catch (e: HttpException) {
            Outcome.Err(httpExceptionToDomain(e))
        } catch (e: SocketTimeoutException) {
            Outcome.Err(DomainError.LlmFailure(reason = LlmFailureReason.TIMEOUT, cause = e))
        } catch (e: IOException) {
            Outcome.Err(DomainError.LlmFailure(reason = LlmFailureReason.SERVER_ERROR, cause = e))
        }
    }

    private fun mapResponse(response: DeepSeekChatResponse): Outcome<List<Candidate>, DomainError> {
        if (response.choices.isEmpty()) {
            return Outcome.Err(DomainError.LlmFailure(reason = LlmFailureReason.INVALID_RESPONSE))
        }
        val perCandidateTokens =
            response.usage?.let { usage ->
                usage.completionTokens / response.choices.size.coerceAtLeast(1)
            }
        val candidates =
            response.choices.map { choice ->
                Candidate(
                    text = choice.message.content.trim(),
                    tokens = perCandidateTokens,
                )
            }
        return Outcome.Ok(candidates)
    }

    private fun httpExceptionToDomain(e: HttpException): DomainError.LlmFailure {
        val reason =
            when (e.code()) {
                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> LlmFailureReason.AUTH
                HTTP_TOO_MANY_REQUESTS -> LlmFailureReason.RATE_LIMITED
                in HTTP_INTERNAL_ERROR_RANGE -> LlmFailureReason.SERVER_ERROR
                else -> LlmFailureReason.INVALID_RESPONSE
            }
        return DomainError.LlmFailure(reason = reason, cause = e)
    }

    companion object {
        const val DEFAULT_KEY_NAME: String = "llm.deepseek.api_key"
        const val DEFAULT_MODEL: String = "deepseek-chat"
        const val BASE_URL: String = "https://api.deepseek.com/"

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_INTERNAL_ERROR_RANGE = 500..599

        /**
         * Production factory. Builds a Retrofit-backed [DeepSeekApi] over the
         * shared candidate-generation OkHttp client (5s connect / 8s
         * read+write per SPEC §1.4) and the [NetworkModule] JSON config.
         */
        fun create(
            keyStore: SecureKeyStore,
            client: OkHttpClient = NetworkModule.candidateGenerationClient(),
            baseUrl: String = BASE_URL,
            model: String = DEFAULT_MODEL,
            keyName: String = DEFAULT_KEY_NAME,
        ): DeepSeekProvider {
            val api =
                NetworkModule.retrofitBuilder(baseUrl = baseUrl, client = client)
                    .build()
                    .create(DeepSeekApi::class.java)
            return DeepSeekProvider(api = api, keyStore = keyStore, model = model, keyName = keyName)
        }
    }
}
