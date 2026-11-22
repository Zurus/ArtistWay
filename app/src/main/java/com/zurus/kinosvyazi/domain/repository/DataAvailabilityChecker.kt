package com.zurus.kinosvyazi.domain.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result

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
