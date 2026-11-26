package com.zurus.cinethread.domain.usecase

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.repository.DataAvailabilityChecker
import javax.inject.Inject

/**
 * Проверка доступности TMDB для индикатора на экране запуска
 * (docs/01_concept.md, §2.1: «индикатор доступности данных, если игра
 * не может подключиться к TMDB»).
 */
class CheckDataAvailabilityUseCase @Inject constructor(
    private val dataAvailabilityChecker: DataAvailabilityChecker,
) {

    suspend operator fun invoke(): Result<Unit, AppError> =
        dataAvailabilityChecker.check()
}
