package com.zurus.kinosvyazi.data.remote

import com.zurus.kinosvyazi.core.common.AppError
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

/**
 * Классификация ошибок TMDB в `AppError` (docs/03_data_layer.md §6):
 *
 * - `IOException` сети → `Offline` (не удалось подключиться) или `Network` (таймаут и прочее);
 * - HTTP `429` → `RateLimit` (с `Retry-After`, если сервер указал);
 * - HTTP `5xx` → `Server`;
 * - прочие HTTP `4xx` → `DataInvalid`;
 * - ошибка десериализации → `DataInvalid`.
 */
object TmdbErrorMapper {

    fun toAppError(throwable: Throwable): AppError = when (throwable) {
            is HttpException -> when (val code = throwable.code()) {
                429 -> AppError.RateLimit(
                    retryAfterSeconds = parseRetryAfterSeconds(
                        throwable.response()?.headers()?.get(HEADER_RETRY_AFTER),
                    ),
                )
                in 500..599 -> AppError.Server
                else -> AppError.DataInvalid("HTTP $code")
            }

            is ConnectException, is UnknownHostException -> AppError.Offline
            is IOException -> AppError.Network
            is SerializationException -> AppError.DataInvalid("Failed to parse TMDB response")
            else -> AppError.Network
        }

    private fun parseRetryAfterSeconds(headerValue: String?): Int? =
        headerValue?.trim()?.toIntOrNull()

    private const val HEADER_RETRY_AFTER = "Retry-After"
}
