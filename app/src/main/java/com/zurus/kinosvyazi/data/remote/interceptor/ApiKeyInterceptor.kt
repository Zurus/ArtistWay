package com.zurus.kinosvyazi.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Добавляет `api_key` к каждому запросу TMDB.
 * Ключ приходит из `BuildConfig.TMDB_API_KEY` (local.properties, git-ignored).
 */
class ApiKeyInterceptor(
    private val apiKey: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder()
            .addQueryParameter("api_key", apiKey)
            .build()
        return chain.proceed(original.newBuilder().url(url).build())
    }
}
