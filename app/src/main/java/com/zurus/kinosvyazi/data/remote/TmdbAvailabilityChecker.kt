package com.zurus.kinosvyazi.data.remote

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.data.remote.api.TmdbConfigurationApi
import com.zurus.kinosvyazi.domain.repository.DataAvailabilityChecker
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проверка доступности TMDB лёгким запросом `/configuration`
 * (docs/01_concept.md, §2.1 — индикатор доступности данных).
 *
 * Ошибки классифицируются общим [TmdbErrorMapper]:
 * сбой сети — [AppError.Network], ошибка сервера — [AppError.Server].
 */
@Singleton
class TmdbAvailabilityChecker @Inject constructor(
    private val configurationApi: TmdbConfigurationApi,
) : DataAvailabilityChecker {

    override suspend fun check(): Result<Unit, AppError> =
        try {
            configurationApi.getConfiguration()
            Result.success(Unit)
        } catch (e: HttpException) {
            Timber.w(e, "TMDB /configuration: ошибка сервера")
            Result.failure(TmdbErrorMapper.toAppError(e))
        } catch (e: IOException) {
            Timber.w(e, "TMDB /configuration: нет соединения")
            Result.failure(TmdbErrorMapper.toAppError(e))
        }
}
