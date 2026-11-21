package com.zurus.kinosvyazi.domain.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.Movie

/**
 * Чтение актеров и их фильмографий.
 *
 * Источник правды — TMDB + локальный кэш; стратегия
 * cache-first / offline-first (docs/03_data_layer.md §5).
 */
interface ActorRepository {

    /** Возвращает актёра по TMDB ID. */
    suspend fun getActor(actorId: Int): Result<Actor, AppError>

    /**
     * Возвращает фильмы актёра (фильмографию).
     * Используется на экране выбора фильма (docs/01_concept.md, §2.5).
     */
    suspend fun getActorMovies(actorId: Int): Result<List<Movie>, AppError>
}
