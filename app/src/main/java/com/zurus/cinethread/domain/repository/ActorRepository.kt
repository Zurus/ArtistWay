package com.zurus.cinethread.domain.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.Actor
import com.zurus.cinethread.domain.model.Movie

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
