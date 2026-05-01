package com.stylemirror.infra.llm.deepseek

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit binding for the DeepSeek chat-completions endpoint.
 *
 * The base URL is configured at the [retrofit2.Retrofit] level
 * ([DeepSeekProvider.create] sets it). The `Authorization` header is passed
 * per-call so the API key — which we pull from [com.stylemirror.domain.security.SecureKeyStore]
 * — never gets cached on the OkHttpClient or surfaced via a static interceptor
 * (which is harder to scrub from logs).
 */
internal interface DeepSeekApi {
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: DeepSeekChatRequest,
    ): DeepSeekChatResponse
}
