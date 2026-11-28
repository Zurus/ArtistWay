package com.zurus.cinethread.data.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.util.Budget
import com.zurus.cinethread.domain.util.GenCache

/**
 * Дешёвый поиск пары глубины 2 (docs/debugging.md D5): пересечение
 * множеств ко-актёров вместо обобщённого BFS — «взрыв» среднего слоя
 * больше не происходит.
 *
 * Для пары (A, B) загружаются фильмографии A и B и составы их фильмов
 * (через [GenCache]; реальные запросы учитываются в общем бюджете D1),
 * пересечение считается в памяти: общий ко-актёр X → путь
 * A→fA→X→fB→B — два перехода игрока.
 *
 * Защита от d=1 (пара, у которой общий фильм, — тривиальная и не должна
 * маскироваться под d=2):
 * - A и B делят фильм (`filmsA ∩ filmsB ≠ ∅`) — пара отбрасывается;
 * - явная проверка `fA ≠ fB` при совпадении ко-актёра (регресс).
 *
 * Данные актёра переиспользуются между парами через [GenCache]/Room:
 * повторная пара с тем же A не запрашивает его фильмографию снова.
 */
internal sealed interface DepthTwoResult {

    /** Общий ко-актёр найден: A→[startFilmId]→X→[targetFilmId]→B. */
    data class Found(
        val startFilmId: Int,
        val coActorId: Int,
        val targetFilmId: Int,
    ) : DepthTwoResult

    /** Общего ко-актёра нет (или d=1 пара) — попробовать следующую пару. */
    data object NoCommon : DepthTwoResult

    /** Пер-пара бюджет исчерпан — пропустить пару (семантика D1). */
    data object PairBudgetExceeded : DepthTwoResult

    /** Суммарный бюджет генерации исчерпан — остановить поиск. */
    data object OuterBudgetExceeded : DepthTwoResult

    /** Ошибка данных (сеть, сервер, Room) — реальный сбой. */
    data class DataError(val error: AppError) : DepthTwoResult
}

internal object DepthTwoPairSearch {

    /**
     * Ищет общего ко-актёра [startId] и [targetId].
     *
     * Фильмы расширяются чередованием (A, B, A, B…) и останавливаются,
     * как только найден общий X, — не грузим фильмы, которые не нужны
     * (ранняя остановка, docs/04_algorithms.md §5.5).
     *
     * @param pairBudget per-пара бюджет D1 (узлы + wall-clock); реальные
     * запросы учитываются в суммарном бюджете через [cache].
     */
    suspend fun find(
        startId: Int,
        targetId: Int,
        cache: GenCache,
        pairBudget: Budget,
    ): DepthTwoResult {
        if (startId == targetId) return DepthTwoResult.NoCommon
        if (pairBudget.exhausted()) return DepthTwoResult.PairBudgetExceeded

        // Подготовка: фильмографии обоих концов + отсев d=1 (общий фильм).
        val filmsA = mutableListOf<Int>()
        val filmsB = mutableListOf<Int>()
        when (val prep = prepare(startId, targetId, cache, pairBudget)) {
            is Prepare.Ready -> {
                filmsA.addAll(prep.filmsA)
                filmsB.addAll(prep.filmsB)
            }
            is Prepare.Done -> return prep.result
        }

        // X -> первый фильм A (соотв. B), где встречается X
        // (порядок TMDB — детерминированный).
        val coActorsOfStart = linkedMapOf<Int, Int>()
        val coActorsOfTarget = linkedMapOf<Int, Int>()
        val limit = maxOf(filmsA.size, filmsB.size)
        for (i in 0 until limit) {
            if (i < filmsA.size) {
                checkFilm(
                    filmId = filmsA[i],
                    own = coActorsOfStart,
                    other = coActorsOfTarget,
                    startId = startId,
                    targetId = targetId,
                    cache = cache,
                    pairBudget = pairBudget,
                    filmIsStart = true,
                )?.let { return it }
            }
            if (i < filmsB.size) {
                checkFilm(
                    filmId = filmsB[i],
                    own = coActorsOfTarget,
                    other = coActorsOfStart,
                    startId = startId,
                    targetId = targetId,
                    cache = cache,
                    pairBudget = pairBudget,
                    filmIsStart = false,
                )?.let { return it }
            }
        }
        return DepthTwoResult.NoCommon
    }

    /**
     * Подготовка к [find]: фильмографии обоих концов и отсев d=1-пар
     * (общий фильм — тривиальная пара, не должна маскироваться под d=2,
     * регресс D5).
     */
    private sealed interface Prepare {

        /** Фильмографии обоих концов загружены, пар можно искать. */
        data class Ready(val filmsA: List<Int>, val filmsB: List<Int>) : Prepare

        /** Итог без поиска (бюджет, ошибка данных, d=1/пустые списки). */
        data class Done(val result: DepthTwoResult) : Prepare
    }

    private suspend fun prepare(
        startId: Int,
        targetId: Int,
        cache: GenCache,
        pairBudget: Budget,
    ): Prepare {
        pairBudget.recordNodeVisited()
        val filmsA = when (val loaded = cache.actorMovieIds(startId)) {
            is Result.Failure -> return Prepare.Done(cacheFailure(loaded.error))
            is Result.Success -> loaded.value
        }
        if (filmsA.isEmpty()) return Prepare.Done(DepthTwoResult.NoCommon)

        if (pairBudget.exhausted()) return Prepare.Done(DepthTwoResult.PairBudgetExceeded)
        pairBudget.recordNodeVisited()
        val filmsB = when (val loaded = cache.actorMovieIds(targetId)) {
            is Result.Failure -> return Prepare.Done(cacheFailure(loaded.error))
            is Result.Success -> loaded.value
        }
        if (filmsB.isEmpty()) return Prepare.Done(DepthTwoResult.NoCommon)

        // Защита 1: общий фильм A и B означает расстояние 1 — пара
        // тривиальная (minSteps) и не считается парой глубины 2.
        if (filmsA.any { film -> film in filmsB }) {
            return Prepare.Done(DepthTwoResult.NoCommon)
        }
        return Prepare.Ready(filmsA, filmsB)
    }

    /**
     * Загружает состав фильма [filmId] и заполняет [own] (X → фильм),
     * проверяя совпадения с [other].
     *
     * @return `null` — совпадений в этом фильме нет (продолжить),
     * либо итоговый [DepthTwoResult] (Found / бюджет / ошибка данных).
     */
    private suspend fun checkFilm(
        filmId: Int,
        own: MutableMap<Int, Int>,
        other: Map<Int, Int>,
        startId: Int,
        targetId: Int,
        cache: GenCache,
        pairBudget: Budget,
        filmIsStart: Boolean,
    ): DepthTwoResult? {
        if (pairBudget.exhausted()) return DepthTwoResult.PairBudgetExceeded
        pairBudget.recordNodeVisited() // сам узел фильма
        val cast = when (val loaded = cache.movieCastIds(filmId)) {
            is Result.Failure -> return cacheFailure(loaded.error)
            is Result.Success -> loaded.value
        }
        for (x in cast) {
            if (x == startId || x == targetId) continue
            if (pairBudget.exhausted()) return DepthTwoResult.PairBudgetExceeded
            pairBudget.recordNodeVisited()
            own.putIfAbsent(x, filmId)
            val otherFilmId = other[x] ?: continue
            // Защита 2: fA ≠ fB — если X «совпал» через единственный общий
            // фильм, это d=1, а не путь из двух ходов.
            if (otherFilmId == filmId) continue
            return if (filmIsStart) {
                DepthTwoResult.Found(startFilmId = filmId, coActorId = x, targetFilmId = otherFilmId)
            } else {
                DepthTwoResult.Found(startFilmId = otherFilmId, coActorId = x, targetFilmId = filmId)
            }
        }
        return null
    }

    /**
     * Ошибка чтения через [GenCache]: исчерпанный суммарный бюджет —
     * [DepthTwoResult.OuterBudgetExceeded] (остановить генерацию),
     * прочая ошибка — [DepthTwoResult.DataError] (сбой).
     */
    private fun cacheFailure(error: AppError): DepthTwoResult =
        if (error is AppError.BudgetExceeded) {
            DepthTwoResult.OuterBudgetExceeded
        } else {
            DepthTwoResult.DataError(error)
        }
}
