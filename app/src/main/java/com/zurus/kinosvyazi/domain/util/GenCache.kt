package com.zurus.kinosvyazi.domain.util

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository

/**
 * Локальная мемоизация в пределах одного вызова алгоритма
 * (docs/04_algorithms.md §5.2).
 *
 * Живёт только внутри одного `generatePair`/`checkDeadEnd` и
 * отбрасывается; глобальный кэш — in-memory слой и Room
 * (обеспечивается самими репозиториями).
 *
 * Учёт бюджета (§5.4): только реальный вызов репозитория растит
 * [Budget.requestsUsed], попадания в локальный кэш — бесплатны.
 * Провал не кэшируется: повторный вызов попробует загрузить снова.
 */
class GenCache(
    private val actorRepository: ActorRepository,
    private val movieRepository: MovieRepository,
    private val budget: Budget,
) {

    private val moviesByActor = mutableMapOf<Int, List<Int>>()
    private val castByMovie = mutableMapOf<Int, List<Int>>()

    /**
     * ID фильмов актёра (`getActorMovieIds` из доков).
     *
     * @return список ID, либо [AppError.BudgetExceeded], если на реальный
     * запрос бюджета уже не хватает, либо ошибка репозитория.
     */
    suspend fun actorMovieIds(actorId: Int): Result<List<Int>, AppError> =
        loadAndCache(moviesByActor, actorId) {
            actorRepository.getActorMovies(actorId).map { movies -> movies.map { it.tmdbId } }
        }

    /** ID актёров в фильме (`getMovieCastIds` из доков). */
    suspend fun movieCastIds(movieId: Int): Result<List<Int>, AppError> =
        loadAndCache(castByMovie, movieId) {
            movieRepository.getMovieCast(movieId).map { actors -> actors.map { it.tmdbId } }
        }

    private suspend fun loadAndCache(
        cache: MutableMap<Int, List<Int>>,
        key: Int,
        load: suspend () -> Result<List<Int>, AppError>,
    ): Result<List<Int>, AppError> {
        cache[key]?.let { return Result.success(it) }
        if (budget.exhausted()) {
            return Result.failure(
                AppError.BudgetExceeded(
                    "requests=${budget.requestsUsed}/${budget.maxRequests}, " +
                        "nodes=${budget.nodesVisited}/${budget.maxNodesVisited}",
                ),
            )
        }
        budget.recordRequest()
        val result = load()
        result.getOrNull()?.let { cache[key] = it }
        return result
    }
}
