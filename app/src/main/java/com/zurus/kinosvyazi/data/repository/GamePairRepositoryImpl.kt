package com.zurus.kinosvyazi.data.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.GenerationBudget
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.GamePairRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.repository.PairGenerationError
import com.zurus.kinosvyazi.domain.util.Bfs
import com.zurus.kinosvyazi.domain.util.BfsResult
import com.zurus.kinosvyazi.domain.util.Budget
import com.zurus.kinosvyazi.domain.util.GenCache
import com.zurus.kinosvyazi.domain.util.PairGenConfig
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

/**
 * Реализация [GamePairRepository] (docs/04_algorithms.md §3,
 * docs/05_roadmap.md, Этап 3).
 *
 * MVP (простой вариант): пары «стартовый актёр → целевой актёр»
 * **зашиты** в [HARDCODED_PAIRS] — все упорядоченные комбинации звёзд
 * из [PAIR_ACTORS]. На каждом запуске список перемешивается по [seed]
 * (детерминированно), пары перебираются по очереди, и каждая проверяется
 * целевым BFS ([Bfs.bfsShortestPath]) в пределах [Budget]:
 *
 * - путь найден и `minSteps <= distance <= maxSteps` → пара выдана,
 *   `shortestPath` взят из BFS-дерева (гарантия решаемости, §3.5);
 * - пути нет (`NotFound`) или пара тривиальна (`d = 1`) → следующая пара;
 * - пара «дорогая» (per-pair бюджет исчерпан, D1) → следующая пара,
 *   генерация не ломается;
 * - исчерпан суммарный бюджет генерации →
 *   [PairGenerationError.BudgetExceeded] (валидное состояние, UI
 *   предлагает «попробовать ещё раз»);
 * - все пары перебраны → [PairGenerationError.NoValidPair].
 *
 * Бюджет двухуровневый (docs/debugging.md D1): [GenCache] учитывает
 * реальные запросы в суммарный бюджет генерации (фабрика
 * `@GenerationBudget`, профиль [Budget.GENERATION_MAX_WALL_CLOCK_MS]);
 * каждый BFS получает собственный per-pair бюджет (wall-clock и узлы),
 * чтобы одна дорогая пара не сжегала весь лимит. `maxRequests`/узлы
 * суммарного бюджета остаются жёсткими верхними границами.
 *
 * Оба конца пары — известные актёры из [PAIR_ACTORS] (в отличие от
 * пулового [com.zurus.kinosvyazi.domain.util.PairGenerator], где цель —
 * любой достигнутый актёр). `PairGenerator` остаётся в домене и станет
 * основой P4 (профили сложности, seed-список «top-N по popularity»).
 */
@Singleton
class GamePairRepositoryImpl @Inject constructor(
    private val actorRepository: ActorRepository,
    private val movieRepository: MovieRepository,
    private val pairGenConfig: PairGenConfig,
    @GenerationBudget private val generationBudgetFactory: () -> Budget,
) : GamePairRepository {

    override suspend fun generatePair(seed: Long?): Result<GamePair, PairGenerationError> {
        val effectiveSeed = seed ?: ThreadLocalRandom.current().nextLong()
        val genBudget = generationBudgetFactory()
        val cache = GenCache(actorRepository, movieRepository, genBudget)
        val rng = Random(effectiveSeed)
        var budgetExceeded = false

        for ((startId, targetId) in HARDCODED_PAIRS.shuffled(rng)) {
            if (genBudget.exhausted()) {
                budgetExceeded = true
                break
            }
            // D1: per-pair бюджет — одна дорогая пара не должна сжигать
            // общий лимит (реальный канал ~1 запрос/с). Запросы учитывает
            // [GenCache] в [genBudget]; здесь — wall-clock и узлы пары.
            val pairBudget = Budget(
                maxRequests = Int.MAX_VALUE,
                maxNodesVisited = PAIR_MAX_NODES_VISITED,
                maxWallClockMs = PAIR_MAX_WALL_CLOCK_MS,
            )
            val bfs = when (
                val outcome = Bfs.bfsShortestPath(
                    startId, targetId, pairGenConfig.maxSteps, cache, pairBudget,
                )
            ) {
                is Result.Failure -> when (val error = outcome.error) {
                    is AppError.BudgetExceeded -> {
                        budgetExceeded = true
                        break
                    }
                    else -> return Result.failure(PairGenerationError.DataError(error))
                }
                is Result.Success -> outcome.value
            }
            when (bfs) {
                is BfsResult.Found -> {
                    // Пара «одна общая картина» (d = 1) — тривиальная игра,
                    // отсекается minSteps (PairGenConfig.allowTrivial).
                    if (bfs.distance < pairGenConfig.minSteps) continue
                    val path = Bfs.reconstructChain(startId, targetId, bfs.parents)
                    if (path.isEmpty()) continue
                    return toDomain(startId, targetId, path, effectiveSeed)
                }
                BfsResult.NotFound -> Unit
                BfsResult.BudgetExceeded -> {
                    // D1: пара дороже per-pair лимита — пробуем следующую,
                    // генерация продолжается (раньше здесь было `break`).
                    Unit
                }
            }
        }

        return if (budgetExceeded) {
            Result.failure(
                PairGenerationError.BudgetExceeded(
                    "requests=${genBudget.requestsUsed}/${genBudget.maxRequests}, " +
                        "wallClock=${genBudget.maxWallClockMs}ms",
                ),
            )
        } else {
            Result.failure(PairGenerationError.NoValidPair)
        }
    }

    private suspend fun toDomain(
        startId: Int,
        targetId: Int,
        shortestPath: List<ChainStep>,
        seed: Long,
    ): Result<GamePair, PairGenerationError> {
        val start = when (val loaded = actorRepository.getActor(startId)) {
            is Result.Failure -> return Result.failure(PairGenerationError.DataError(loaded.error))
            is Result.Success -> loaded.value
        }
        val target = when (val loaded = actorRepository.getActor(targetId)) {
            is Result.Failure -> return Result.failure(PairGenerationError.DataError(loaded.error))
            is Result.Success -> loaded.value
        }
        return Result.Success(
            GamePair(
                startActor = start,
                targetActor = target,
                shortestPath = shortestPath,
                seed = seed,
            ),
        )
    }

    /**
     * Звёзды с проверенными TMDB `person_id` (MVP). Все — кастовые
     * голливудские актёры с большим пересечением составов: почти любая
     * пара достижима за 2–4 шага; нерешаемые в пределах `maxSteps`
     * отбраковываются целевым BFS в момент генерации.
     *
     * Расширение списка = добавить ID сюда (P4: заменить на seed-список
     * «top-N по popularity» из данных TMDB).
     */
    private companion object {
        /**
         * D1: per-pair лимит узлов (≈ дефолтный §3.6): один BFS не может
         * замахнуться на бесконечное раскрытие.
         */
        private const val PAIR_MAX_NODES_VISITED = 1_500

        /**
         * D1: per-pair wall-clock, мс. На канале ~1 запрос/с это ~30
         * реальных запросов — достаточно, чтобы дорогая пара уступила
         * место следующим (почти бесплатным: GenCache уже тёплый).
         */
        private const val PAIR_MAX_WALL_CLOCK_MS = 30_000L

        val PAIR_ACTORS: List<Int> = listOf(
            31, // Tom Hanks
            38, // Harrison Ford
            39, // Robin Williams
            42, // Michael Douglas
            122, // Robert De Niro
            287, // Brad Pitt
            385, // Johnny Depp
            5381, // Meryl Streep
            9226, // Tom Cruise
            11464, // Christian Bale
            1972, // Morgan Freeman
            2518, // Leonardo DiCaprio
            2871, // Robert Downey Jr.
        )

        /** Все упорядоченные пары (стартовый, целевой), 13 × 12 = 156. */
        val HARDCODED_PAIRS: List<Pair<Int, Int>> = PAIR_ACTORS.flatMap { start ->
            PAIR_ACTORS.filter { it != start }.map { start to it }
        }
    }
}
