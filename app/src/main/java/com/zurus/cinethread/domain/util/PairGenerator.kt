package com.zurus.cinethread.domain.util

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.repository.ActorRepository
import com.zurus.cinethread.domain.repository.MovieRepository
import kotlin.random.Random

/**
 * Конфигурация генерации пары (docs/04_algorithms.md §3.1).
 *
 * Дефолты — рекомендованные значения таблицы §3.6.
 *
 * @property minSteps минимальная длина пути (отсекает тривиальные пары).
 * @property preferredMinSteps нижняя граница «предпочтительного» диапазона.
 * @property preferredMaxSteps верхняя граница «предпочтительного» диапазона.
 * @property maxSteps жёсткий предел глубины BFS генерации.
 * @property startPoolSize сколько стартовых актёров перебрать.
 * @property maxFrontierPerActor ограничение fan-out при раскрытии (§5.3).
 * @property targetSampleSize сколько целей проверить на одном старте.
 * @property allowTrivial разрешать пары с `d = 1`.
 * @property requireReverseReachability бидирекциональная проверка (§5.6).
 * @property deadEndSearchDepth глубина `checkDeadEnd`; не меньше [maxSteps].
 */
data class PairGenConfig(
    val minSteps: Int = 2,
    val preferredMinSteps: Int = 3,
    val preferredMaxSteps: Int = 5,
    val maxSteps: Int = 6,
    val startPoolSize: Int = 25,
    val maxFrontierPerActor: Int = 50,
    val targetSampleSize: Int = 8,
    val allowTrivial: Boolean = false,
    val requireReverseReachability: Boolean = false,
    val deadEndSearchDepth: Int = 8,
) {

    /**
     * D5 (docs/debugging.md): конфигурация пары **точной глубины** —
     * `minSteps = maxSteps = depth`. Генератор примет только пару, чей
     * кратчайший путь ровно длины [depth] (короткие — как тривиальные).
     */
    fun forDepth(depth: Int): PairGenConfig =
        copy(minSteps = depth, maxSteps = depth)
}

/** Причина отказа генератора (docs/04_algorithms.md §3.2). */
enum class PairFailReason {
    /** Пул стартовых актёров пуст. */
    NO_START_CANDIDATES,

    /** Бюджет исчерпан (валидное состояние, а не исключение). */
    BUDGET_EXCEEDED,

    /** Ни один старт не дал валидной цели. */
    NO_VALID_PAIR,
}

/** Результат генерации пары (§3.2). */
sealed interface PairResult {

    /**
     * Пара с гарантированным решением (§3.5): [shortestPath] существует
     * и его длина не превышает `maxSteps`.
     */
    data class Ok(
        val startActorId: Int,
        val targetActorId: Int,
        val shortestPath: List<ChainStep>,
        val distance: Int,
        val seed: Long,
    ) : PairResult

    /** Пара не найдена; [reason] — причина (валидное состояние). */
    data class Fail(val reason: PairFailReason) : PairResult
}

/**
 * Воспроизводимый генератор решаемых пар (docs/04_algorithms.md §3).
 *
 * Принцип: ограниченный BFS от старта + выбор цели из достигнутых слоёв —
 * путь гарантирован по построению (§3.5). Один и тот же [seed] при
 * одинаковых данных даёт одну и ту же пару (детерминированные shuffle).
 */
class PairGenerator(
    private val actorRepository: ActorRepository,
    private val movieRepository: MovieRepository,
) {

    suspend fun generatePair(
        seed: Long,
        config: PairGenConfig,
        startPool: List<Int>,
        budget: Budget,
    ): Result<PairResult, AppError> {
        if (startPool.isEmpty()) {
            return Result.success(PairResult.Fail(PairFailReason.NO_START_CANDIDATES))
        }
        val rng = Random(seed)
        val cache = GenCache(actorRepository, movieRepository, budget)
        val usedActors = mutableSetOf<Int>()
        val pool = startPool.toMutableList()
        pool.shuffle(rng)

        for (startId in pool) {
            if (budget.exhausted()) {
                return Result.success(PairResult.Fail(PairFailReason.BUDGET_EXCEEDED))
            }
            val layers = when (
                val layersResult = Bfs.boundedBfsLayers(startId, config.maxSteps, config, rng, cache, budget)
            ) {
                is Result.Failure -> return layersResult
                is Result.Success -> layersResult.value
            }
            if (layers.reason == BfsReason.BUDGET_EXCEEDED) {
                return Result.success(PairResult.Fail(PairFailReason.BUDGET_EXCEEDED))
            }
            val candidates = candidateTargets(layers, startId, config)
            if (candidates.isEmpty()) continue
            val ordered = orderByPreference(candidates, config, rng)
            var tried = 0
            for ((targetId, depth) in ordered) {
                if (tried >= config.targetSampleSize) break
                tried += 1
                if (targetId in usedActors) continue
                // 6a: у цели должен быть хотя бы один фильм.
                val targetMovies = when (val loaded = cache.actorMovieIds(targetId)) {
                    is Result.Failure -> return loaded
                    is Result.Success -> loaded.value
                }
                if (targetMovies.isEmpty()) continue
                // 6b: опциональная обратная достижимость (§5.6).
                if (config.requireReverseReachability) {
                    val reverse = Bfs.bfsShortestPath(targetId, startId, config.maxSteps, cache, budget)
                    if (reverse is Result.Failure) return reverse
                    if (reverse.getOrNull()?.reason != BfsReason.FOUND) continue
                }
                // 6c: путь восстанавливается из BFS-дерева старта.
                val path = Bfs.reconstructChain(startId, targetId, layers.parents)
                if (path.isEmpty()) continue
                usedActors += startId
                usedActors += targetId
                return Result.success(PairResult.Ok(startId, targetId, path, depth, seed))
            }
        }
        return Result.success(PairResult.Fail(PairFailReason.NO_VALID_PAIR))
    }

    /** Кандидаты-цели из слоёв `minSteps..maxSteps` (§3.2, шаг 4). */
    private fun candidateTargets(
        layers: LayersResult,
        startId: Int,
        config: PairGenConfig,
    ): List<Pair<Int, Int>> = buildList {
        for (depth in config.minSteps..config.maxSteps) {
            for (actorId in layers.byDepth[depth].orEmpty()) {
                if (actorId == startId) continue
                if (!config.allowTrivial && depth < 2) continue
                add(actorId to depth)
            }
        }
    }

    /**
     * Приоритизация целей (§3.4): сначала «предпочтительный» диапазон
     * длин, затем остальное; внутри групп — seeded shuffle.
     */
    private fun orderByPreference(
        candidates: List<Pair<Int, Int>>,
        config: PairGenConfig,
        rng: Random,
    ): List<Pair<Int, Int>> {
        val preferred = candidates.filter { (_, depth) ->
            depth in config.preferredMinSteps..config.preferredMaxSteps
        }
        val preferredIds = preferred.toSet()
        val others = candidates.filterNot { it in preferredIds }
        return (preferred.shuffled(rng) + others.shuffled(rng))
    }
}
