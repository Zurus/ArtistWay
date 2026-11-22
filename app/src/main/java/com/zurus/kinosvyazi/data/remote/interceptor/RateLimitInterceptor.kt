package com.zurus.kinosvyazi.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Повторные попытки при HTTP `429` (docs/03_data_layer.md §6,
 * docs/05_roadmap.md «Этап 2»): экспоненциальная задержка
 * 1с → 2с → 4с ... до [maxRetries] повторов; если сервер прислал
 * заголовок `Retry-After` (секунды) — он берётся в расчёт.
 *
 * После исчерпания попыток ответ `429` возвращается вызывающему:
 * Retrofit обернёт его в `HttpException`, а [com.zurus.kinosvyazi.data.remote.TmdbErrorMapper]
 * классифицирует как `AppError.RateLimit`.
 *
 * Интерцептор синхронный (OkHttp-потоки); вызывается из репозиториев
 * на IO-диспетчере, поэтому `Thread.sleep` допустим. В тестах [sleeper]
 * подменяется, чтобы не ждать реальные задержки.
 */
class RateLimitInterceptor(
    private val maxRetries: Int = MAX_RETRIES,
    private val baseDelayMillis: Long = BASE_DELAY_MILLIS,
    private val maxDelayMillis: Long = MAX_DELAY_MILLIS,
    private val sleeper: (Long) -> Unit = { delayMillis -> Thread.sleep(delayMillis) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var response = chain.proceed(chain.request())
        while (response.code == HTTP_TOO_MANY_REQUESTS && attempt < maxRetries) {
            val delayMillis = retryDelayMillis(response, attempt)
            response.close()
            if (delayMillis > 0L) {
                sleeper(delayMillis)
            }
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }

    private fun retryDelayMillis(response: Response, attempt: Int): Long {
        val serverDelayMillis = response.header(HEADER_RETRY_AFTER)
            ?.trim()?.toLongOrNull()?.let { (it * MILLIS_PER_SECOND).coerceAtLeast(0L) }
        val exponentialDelayMillis = (baseDelayMillis shl attempt).coerceAtMost(maxDelayMillis)
        return (serverDelayMillis ?: exponentialDelayMillis).coerceAtMost(maxDelayMillis * MAX_RETRY_AFTER_MULTIPLIER)
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HEADER_RETRY_AFTER = "Retry-After"
        const val MAX_RETRIES = 3
        const val BASE_DELAY_MILLIS = 1_000L
        const val MAX_DELAY_MILLIS = 10_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val MAX_RETRY_AFTER_MULTIPLIER = 2L
    }
}
