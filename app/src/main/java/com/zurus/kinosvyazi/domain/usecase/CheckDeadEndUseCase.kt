package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.util.Bfs
import com.zurus.kinosvyazi.domain.util.BfsResult
import com.zurus.kinosvyazi.domain.util.Budget
import com.zurus.kinosvyazi.domain.util.GenCache
import com.zurus.kinosvyazi.domain.util.PairGenConfig
import javax.inject.Inject

/**
 * Причина тупика (docs/04_algorithms.md §4.1).
 */
enum class DeadEndCause {
    /** У текущего актёра нет фильмов. */
    NO_MOVIES,

    /** Во всех фильмах актёра нет других актёров. */
    NO_OTHER_ACTORS,

    /** Пути к цели в пределах глубины поиска нет. */
    NO_PATH,
}

/**
 * Результат проверки тупика (docs/04_algorithms.md §4.2, §4.3).
 */
sealed interface DeadEndStatus {

    /** Партия продолжается; [shortestRemaining] — длина кратчайшего пути к цели. */
    data class Ok(val shortestRemaining: Int) : DeadEndStatus

    /** Тупик — валидное игровое состояние, а не ошибка (§4.5). */
    data class DeadEnd(val cause: DeadEndCause) : DeadEndStatus

    /** Проверка не завершилась (бюджет/ошибка данных) — игрока не наказываем (§4.3). */
    data object Unknown : DeadEndStatus
}

/**
 * Проверка тупика после каждого подтверждённого перехода
 * (docs/04_algorithms.md §4.2).
 *
 * Порядок: дешёвые локальные проверки (нет фильмов / нет других актёров)
 * и только потом глобальный BFS в пределах `deadEndSearchDepth`.
 * Лимита на число ходов игрока нет (§4) — глубина поиска задаётся
 * отдельно и не меньше гарантированной длины пути.
 *
 * Никогда не превращает `NOT_FOUND` в исключение: тупик — игровое
 * состояние (§4.5). Ошибки данных и исчерпанный бюджет — [DeadEndStatus.Unknown].
 */
class CheckDeadEndUseCase @Inject constructor(
    private val actorRepository: ActorRepository,
    private val movieRepository: MovieRepository,
    private val pairGenConfig: PairGenConfig,
    private val budgetFactory: () -> Budget,
) {

    suspend operator fun invoke(session: GameSession): DeadEndStatus {
        val budget = budgetFactory()
        val cache = GenCache(actorRepository, movieRepository, budget)
        val currentId = session.currentActor.tmdbId
        val targetId = session.targetActor.tmdbId

        // 1. Есть ли у актёра фильмы?
        val movies = when (val loaded = cache.actorMovieIds(currentId)) {
            is Result.Failure -> return DeadEndStatus.Unknown
            is Result.Success -> loaded.value
        }
        if (movies.isEmpty()) {
            return DeadEndStatus.DeadEnd(DeadEndCause.NO_MOVIES)
        }

        // 2. Быстрый локальный тупик: нет фильма с «другим» актёром.
        var hasOtherActor = false
        var localCheckComplete = true
        for (movieId in movies) {
            if (budget.exhausted()) {
                localCheckComplete = false
                break
            }
            val cast = when (val loadedCast = cache.movieCastIds(movieId)) {
                is Result.Failure -> return DeadEndStatus.Unknown
                is Result.Success -> loadedCast.value
            }
            if (cast.any { it != currentId }) {
                hasOtherActor = true
                break
            }
        }
        if (!hasOtherActor && localCheckComplete) {
            return DeadEndStatus.DeadEnd(DeadEndCause.NO_OTHER_ACTORS)
        }

        // 3. Глобальная проверка: путь к цели в пределах глубины поиска.
        val bfs = Bfs.bfsShortestPath(currentId, targetId, pairGenConfig.deadEndSearchDepth, cache, budget)
        return when (bfs) {
            is Result.Failure -> DeadEndStatus.Unknown
            is Result.Success -> when (val outcome = bfs.value) {
                is BfsResult.Found -> DeadEndStatus.Ok(outcome.distance)
                is BfsResult.NotFound -> DeadEndStatus.DeadEnd(DeadEndCause.NO_PATH)
                is BfsResult.BudgetExceeded -> DeadEndStatus.Unknown
            }
        }
    }
}
