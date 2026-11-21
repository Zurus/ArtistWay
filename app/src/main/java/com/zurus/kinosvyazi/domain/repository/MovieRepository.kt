package com.zurus.kinosvyazi.domain.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.Movie

/**
 * Чтение фильмов и их актёрских составов.
 *
 * Источник правды — TMDB + локальный кэш; стратегия
 * cache-first / offline-first (docs/03_data_layer.md §5).
 */
interface MovieRepository {

    /** Возвращает фильм по TMDB ID. */
    suspend fun getMovie(movieId: Int): Result<Movie, AppError>

    /**
     * Возвращает актёрский состав фильма.
     * Используется на экране выбора следующего актёра (docs/01_concept.md, §2.6).
     */
    suspend fun getMovieCast(movieId: Int): Result<List<Actor>, AppError>
}
