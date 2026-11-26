package com.zurus.cinethread.domain.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result

/**
 * Проверка доступности источника данных (TMDB)
 * (docs/01_concept.md, §2.1: индикатор доступности данных на экране запуска).
 *
 * Реализация — в data-слое; домен знает только контракт.
 */
fun interface DataAvailabilityChecker {

    /**
     * Проверяет, что TMDB отвечает.
     *
     * @return успех, если источник данных доступен; [AppError] — если нет.
     */
    suspend fun check(): Result<Unit, AppError>
}
