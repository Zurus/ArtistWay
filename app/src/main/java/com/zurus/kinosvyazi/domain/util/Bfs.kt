package com.zurus.kinosvyazi.domain.util

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.ChainStep
import kotlin.random.Random

/**
 * Причина завершения BFS (docs/04_algorithms.md §2.1, §3.3).
 *
 * `NOT_FOUND` и `BUDGET_EXCEEDED` — валидные состояния, а не ошибки
 * (docs/04_algorithms.md §4.5).
 */
enum class BfsReason {
    /** BFS завершён штатно (используется [LayersResult]). */
    OK,

    /** Цель достигнута. */
    FOUND,

    /** Пути в пределах `maxSteps` нет. */
    NOT_FOUND,

    /** Бюджет исчерпан до завершения проверки. */
    BUDGET_EXCEEDED,
}

/** Переход через фильм: от актёра [prevActorId] через фильм [movieId]. */
data class ParentLink(
    val movieId: Int,
    val prevActorId: Int,
)

/** Результат BFS к цели (docs/04_algorithms.md §2.1). */
sealed interface BfsResult {

    val reason: BfsReason

    /** Цель достигнута; [parents] позволяет восстановить путь. */
    data class Found(val distance: Int, val parents: Map<Int, ParentLink>) : BfsResult {
        override val reason: BfsReason = BfsReason.FOUND
    }

    /** Пути в пределах `maxSteps` нет — валидное игровое состояние. */
    data object NotFound : BfsResult {
        override val reason: BfsReason = BfsReason.NOT_FOUND
    }

    /** Бюджет исчерпан до завершения проверки. */
    data object BudgetExceeded : BfsResult {
        override val reason: BfsReason = BfsReason.BUDGET_EXCEEDED
    }
}

/** Результат BFS без цели: достигнутые актёры по слоям (§3.3). */
data class LayersResult(
    val byDepth: Map<Int, List<Int>>,
    val parents: Map<Int, ParentLink>,
    val reason: BfsReason,
) {

    companion object {
        fun ok(byDepth: Map<Int, List<Int>>, parents: Map<Int, ParentLink>): LayersResult =
            LayersResult(byDepth, parents, BfsReason.OK)

        fun budgetExceeded(): LayersResult =
            LayersResult(emptyMap(), emptyMap(), BfsReason.BUDGET_EXCEEDED)
    }
}

/**
 * Ограниченный BFS по ленивому графу актёров (docs/04_algorithms.md §2, §3.3).
 *
 * Граф не хранится целиком: каждый шаг раскрывает актёра в множество
 * соседних актёров через фильмы. Все обращения к данным — через [GenCache],
 * стоимость — через [Budget].
 */
object Bfs {

    /**
     * Кратчайший путь от [startId] к [targetId] в шагах игрока (§2.1).
     *
     * Останавливается на первом попадании в [targetId] — это гарантирует
     * минимальное расстояние (§2.3).
     *
     * @param maxSteps предел глубины в шагах игрока.
     * @param cache источник данных с бюджетным учётом.
     * @param budget стоимость раскрытия списывается в него.
     * @return [BfsResult] (FOUND/NOT_FOUND/BUDGET_EXCEEDED — валидные
     * состояния), либо ошибка данных (сеть/сервер) — реальный сбой.
     */
    suspend fun bfsShortestPath(
        startId: Int,
        targetId: Int,
        maxSteps: Int,
        cache: GenCache,
        budget: Budget,
    ): Result<BfsResult, AppError> {
        if (startId == targetId) {
            return Result.success(BfsResult.Found(distance = 0, parents = emptyMap()))
        }
        val visited = mutableSetOf(startId)
        var frontier = listOf(startId)
        val parents = mutableMapOf<Int, ParentLink>()
        var depth = 0

        while (depth < maxSteps && frontier.isNotEmpty()) {
            if (budget.exhausted()) {
                return Result.success(BfsResult.BudgetExceeded)
            }
            val nextFrontier = mutableListOf<Int>()
            for (actorId in frontier) {
                budget.recordNodeVisited()
                val movies = when (val loaded = cache.actorMovieIds(actorId)) {
                    is Result.Failure -> return loaded
                    is Result.Success -> loaded.value
                }
                for (movieId in movies) {
                    val cast = when (val loadedCast = cache.movieCastIds(movieId)) {
                        is Result.Failure -> return loadedCast
                        is Result.Success -> loadedCast.value
                    }
                    for (nextId in cast) {
                        if (nextId == actorId) continue
                        if (nextId in visited) continue
                        visited += nextId
                        parents[nextId] = ParentLink(movieId, actorId)
                        if (nextId == targetId) {
                            return Result.success(BfsResult.Found(depth + 1, parents.toMap()))
                        }
                        nextFrontier += nextId
                    }
                }
            }
            frontier = nextFrontier
            depth += 1
        }
        return Result.success(BfsResult.NotFound)
    }

    /**
     * BFS без цели: собирает все достигнутые актёры по слоям до [maxSteps] (§3.3).
     *
     * Отличается от [bfsShortestPath] тем, что не останавливается на цели,
     * а раскрывает фронты до предела глубины. Возвращает [LayersResult.parents]
     * для восстановления любого пути.
     *
     * Fan-out ограничен `config.maxFrontierPerActor` (§5.3).
     */
    suspend fun boundedBfsLayers(
        startId: Int,
        maxSteps: Int,
        config: PairGenConfig,
        rng: Random,
        cache: GenCache,
        budget: Budget,
    ): Result<LayersResult, AppError> {
        val visited = mutableSetOf(startId)
        var frontier = listOf(startId)
        val parents = mutableMapOf<Int, ParentLink>()
        val byDepth = mutableMapOf<Int, List<Int>>(0 to listOf(startId))

        for (depth in 1..maxSteps) {
            if (budget.exhausted()) {
                return Result.success(LayersResult.budgetExceeded())
            }
            val nextFrontier = mutableListOf<Int>()
            for (actorId in frontier) {
                budget.recordNodeVisited()
                val movies = when (val loaded = cache.actorMovieIds(actorId)) {
                    is Result.Failure -> return loaded
                    is Result.Success -> loaded.value
                }
                val limitedMovies = limitCandidates(movies, config.maxFrontierPerActor, rng)
                for (movieId in limitedMovies) {
                    val cast = when (val loadedCast = cache.movieCastIds(movieId)) {
                        is Result.Failure -> return loadedCast
                        is Result.Success -> loadedCast.value
                    }
                    val limitedCast = limitCandidates(cast, config.maxFrontierPerActor, rng)
                    for (nextId in limitedCast) {
                        if (nextId == actorId) continue
                        if (nextId in visited) continue
                        visited += nextId
                        parents[nextId] = ParentLink(movieId, actorId)
                        nextFrontier += nextId
                    }
                }
            }
            if (nextFrontier.isEmpty()) break
            byDepth[depth] = nextFrontier
            frontier = nextFrontier
        }
        return Result.success(LayersResult.ok(byDepth.toMap(), parents.toMap()))
    }

    /**
     * Восстановление цепочки из `parents` (§2.2): идём от цели назад
     * и разворачиваем. [ChainStep.stepIndex] нумеруется с 1 от старта.
     *
     * @return шаги пути либо пустой список, если путь не восстанавливается
     * (защитный выход; с корректным `parents` из BFS не возникает).
     */
    fun reconstructChain(
        startId: Int,
        targetId: Int,
        parents: Map<Int, ParentLink>,
    ): List<ChainStep> {
        if (startId == targetId) return emptyList()
        val steps = mutableListOf<ChainStep>()
        var cursor = targetId
        while (cursor != startId) {
            val link = parents[cursor] ?: return emptyList()
            steps += ChainStep(
                stepIndex = 0,
                fromActorId = link.prevActorId,
                movieId = link.movieId,
                toActorId = cursor,
            )
            cursor = link.prevActorId
        }
        return steps.reversed().mapIndexed { index, step ->
            step.copy(stepIndex = index + 1)
        }
    }

    /**
     * Детерминированное ограничение fan-out (§5.3): seeded shuffle + take.
     * `sampleDeterministic` из доков: конкретная стратегия — за пределами
     * базового алгоритма (§7), воспроизводимость по seed гарантирована.
     */
    internal fun <T> limitCandidates(items: List<T>, limit: Int, rng: Random): List<T> {
        if (limit <= 0 || items.size <= limit) return items
        val copy = items.toMutableList()
        copy.shuffle(rng)
        return copy.take(limit)
    }
}
