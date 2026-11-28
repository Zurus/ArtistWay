package com.zurus.cinethread.data.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.GenerationBudget
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.repository.ActorRepository
import com.zurus.cinethread.domain.repository.GamePairRepository
import com.zurus.cinethread.domain.repository.MovieRepository
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.util.Bfs
import com.zurus.cinethread.domain.util.BfsResult
import com.zurus.cinethread.domain.util.Budget
import com.zurus.cinethread.domain.util.GenCache
import com.zurus.cinethread.domain.util.PairGenConfig
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

/**
 * Реализация [GamePairRepository] (docs/04_algorithms.md §3,
 * docs/05_roadmap.md, Этап 3).
 *
 * MVP (простой вариант): пары «стартовый актёр → целевой актёр»
 * **зашиты** в [HARDCODED_PAIRS] — все упорядоченные комбинации актёров
 * пула из [PAIR_ACTORS]. На каждом запуске список перемешивается по [seed]
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
 * D5 (docs/debugging.md): глубина пары выбирается игроком (2..4),
 * `generatePair(seed, depth)` строит конфиг `minSteps = maxSteps = depth`.
 * Глубина 2 — целевой BFS заменён дешёвым поиском [DepthTwoPairSearch]
 * (пересечение ко-актёрных множеств: взрыв среднего слоя исключён);
 * глубины 3–4 — целевой BFS без изменений (maxSteps = depth).
 *
 * D8 (docs/debugging.md): **банк пар вынесен из репозитория** в
 * стратегии [PairSource] (`data/pairsource`): [com.zurus.cinethread.data.pairsource.BankPairSource]
 * (строго, без фолбэка) и [com.zurus.cinethread.data.pairsource.AutoPairSource]
 * (банк → онлайн, 0 запросов, когда глубина покрыта). Репозиторий —
 * чистый онлайн-поиск (режим `pairSource = online` ≡ этому классу).
 *
 * Бюджет двухуровневый (docs/debugging.md D1): [GenCache] учитывает
 * реальные запросы в суммарный бюджет генерации (фабрика
 * `@GenerationBudget`, профиль [Budget.GENERATION_MAX_WALL_CLOCK_MS]);
 * каждый BFS получает собственный per-pair бюджет (wall-clock и узлы),
 * чтобы одна дорогая пара не сжегала весь лимит. `maxRequests`/узлы
 * суммарного бюджета остаются жёсткими верхними границами.
 *
 * Оба конца пары — известные актёры из [PAIR_ACTORS] (в отличие от
 * пулового [com.zurus.cinethread.domain.util.PairGenerator], где цель —
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

    override suspend fun generatePair(
        seed: Long?,
        depth: Int,
    ): Result<GamePair, PairGenerationError> {
        if (depth !in SUPPORTED_DEPTHS) {
            return Result.failure(
                PairGenerationError.DataError(
                    AppError.DataInvalid("Глубина пары $depth: поддерживается ${SUPPORTED_DEPTHS}"),
                ),
            )
        }
        // D5: пара точной глубины — minSteps = maxSteps = depth.
        val config = pairGenConfig.forDepth(depth)
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
            // D5: глубина 2 — пересечение ко-актёрных множеств (без BFS и
            // взрыва среднего слоя), глубины 3–4 — целевой BFS.
            val attempt = if (depth == 2) {
                depthTwoAttempt(startId, targetId, cache, pairBudget, effectiveSeed)
            } else {
                bfsAttempt(startId, targetId, config, cache, pairBudget, effectiveSeed)
            }
            when (attempt) {
                is PairAttempt.Ready -> return Result.Success(attempt.pair)
                PairAttempt.Next -> continue
                PairAttempt.Stop -> {
                    budgetExceeded = true
                    break
                }
                is PairAttempt.Fail -> return Result.failure(attempt.error)
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

    /**
     * Результат одной попытки в цикле пар (D1/D5): пара найдена /
     * следующая пара (нет пути, тривиальная, дороже per-pair лимита) /
     * стоп генерации (суммарный бюджет) / сбой данных.
     */
    private sealed interface PairAttempt {
        data class Ready(val pair: GamePair) : PairAttempt
        data object Next : PairAttempt
        data object Stop : PairAttempt
        data class Fail(val error: PairGenerationError) : PairAttempt
    }

    private suspend fun depthTwoAttempt(
        startId: Int,
        targetId: Int,
        cache: GenCache,
        pairBudget: Budget,
        seed: Long,
    ): PairAttempt = when (val outcome = DepthTwoPairSearch.find(startId, targetId, cache, pairBudget)) {
        is DepthTwoResult.Found ->
            when (val domain = toDomain(startId, targetId, depthTwoPath(startId, targetId, outcome), seed)) {
                is Result.Success -> PairAttempt.Ready(domain.value)
                is Result.Failure -> PairAttempt.Fail(domain.error)
            }
        DepthTwoResult.NoCommon,
        DepthTwoResult.PairBudgetExceeded -> PairAttempt.Next
        DepthTwoResult.OuterBudgetExceeded -> PairAttempt.Stop
        is DepthTwoResult.DataError -> PairAttempt.Fail(PairGenerationError.DataError(outcome.error))
    }

    /**
     * Глубины 3–4 (D5): существующий целевой BFS без изменений
     * (`maxSteps = depth` из конфига точной глубины).
     */
    private suspend fun bfsAttempt(
        startId: Int,
        targetId: Int,
        config: PairGenConfig,
        cache: GenCache,
        pairBudget: Budget,
        seed: Long,
    ): PairAttempt = when (
        val outcome = Bfs.bfsShortestPath(startId, targetId, config.maxSteps, cache, pairBudget)
    ) {
        is Result.Failure -> when (val error = outcome.error) {
            // Суммарный бюджет исчерпан — стоп; прочая ошибка — сбой данных.
            is AppError.BudgetExceeded -> PairAttempt.Stop
            else -> PairAttempt.Fail(PairGenerationError.DataError(error))
        }
        is Result.Success -> when (val bfs = outcome.value) {
            is BfsResult.Found -> {
                // Пара «одна общая картина» (d = 1) и короче запрошенной
                // глубины — тривиальная игра, отсекается minSteps
                // (PairGenConfig.allowTrivial).
                if (bfs.distance < config.minSteps) {
                    PairAttempt.Next
                } else {
                    val path = Bfs.reconstructChain(startId, targetId, bfs.parents)
                    if (path.isEmpty()) {
                        PairAttempt.Next
                    } else {
                        when (val domain = toDomain(startId, targetId, path, seed)) {
                            is Result.Success -> PairAttempt.Ready(domain.value)
                            is Result.Failure -> PairAttempt.Fail(domain.error)
                        }
                    }
                }
            }
            BfsResult.NotFound -> PairAttempt.Next
            // D1: пара дороже per-pair лимита — пробуем следующую,
            // генерация продолжается (раньше здесь было `break`).
            BfsResult.BudgetExceeded -> PairAttempt.Next
        }
    }

    /**
     * D5: цепочка глубины 2 из пересечения ко-актёров: A→fA→X→fB→B —
     * два хода игрока, два [ChainStep].
     */
    private fun depthTwoPath(
        startId: Int,
        targetId: Int,
        found: DepthTwoResult.Found,
    ): List<ChainStep> = listOf(
        ChainStep(stepIndex = 1, fromActorId = startId, movieId = found.startFilmId, toActorId = found.coActorId),
        ChainStep(stepIndex = 2, fromActorId = found.coActorId, movieId = found.targetFilmId, toActorId = targetId),
    )

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
     * Пул актёров (D2, docs/debugging.md): 107 TMDB-актёров — данные
     * `tools/generate_start_pool.py` (97 популярных фильмов TMDB,
     * пересечение составов ≥ 2; имена/ID — ответы самого API), плюс
     * 10 звёзд legacy, не вошедших в пул. Дубликаты старого списка
     * удалены в пользу ID из пула (RDJ `2871`→`3223`, Morgan Freeman
     * `1972`→`192`, Christian Bale `11464`→`3894`): одна и та же
     * реальная фигура под двумя ID дала бы «пару» актёра с самим собой.
     *
     * Перегенерация пула — запуск скрипта (нужен TMDB-ключ), код не
     * меняется. P4: заменить список на «top-N по popularity» из TMDB.
     */
    internal companion object {
        /** D5: поддерживаемые глубины пары (выбор на стартовом экране). */
        private val SUPPORTED_DEPTHS = 2..4

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
            16851, // Josh Brolin
            3223, // Robert Downey Jr.
            16828, // Chris Evans
            505710, // Zendaya
            1136406, // Tom Holland
            103, // Mark Ruffalo
            1813, // Anne Hathaway
            2283, // Stanley Tucci
            9281, // Elizabeth Banks
            27972, // Josh Hutcherson
            55636, // Donald Sutherland
            57755, // Woody Harrelson
            72129, // Jennifer Lawrence
            74568, // Chris Hemsworth
            237455, // Sam Claflin
            530025, // Willow Shields
            1190668, // Timothée Chalamet
            1211946, // P.J. Byrne
            1373737, // Florence Pugh
            1233, // Philip Seymour Hoffman
            1245, // Scarlett Johansson
            1640, // Stellan Skarsgård
            2954, // Jeffrey Wright
            3810, // Javier Bardem
            3895, // Michael Caine
            6162, // Paul Bettany
            8691, // Zoe Saldaña
            9048, // Clark Gregg
            15277, // Jon Favreau
            20089, // Jena Malone
            22970, // Peter Dinklage
            44079, // Charlotte Rampling
            55936, // Jemaine Clement
            71580, // Benedict Cumberbatch
            83002, // Jessica Chastain
            89599, // Eric Bauza
            91606, // Tom Hiddleston
            96066, // Liam Hemsworth
            117642, // Jason Momoa
            225730, // Ryunosuke Kamiki
            543530, // Dave Bautista
            932967, // Mahershala Ali
            933238, // Rebecca Ferguson
            1880755, // Andrew Schulz
            19, // Allison Janney
            192, // Morgan Freeman
            515, // Glenn Close
            887, // Owen Wilson
            1229, // Jeff Bridges
            1231, // Julianne Moore
            1892, // Matt Damon
            1896, // Don Cheadle
            2037, // Cillian Murphy
            2231, // Samuel L. Jackson
            2963, // Nicolas Cage
            3141, // Marisa Tomei
            3894, // Christian Bale
            8210, // Wes Bentley
            10401, // Blythe Danner
            10980, // Daniel Radcliffe
            10989, // Rupert Grint
            11288, // Robert Pattinson
            12052, // Gwyneth Paltrow
            13014, // Toby Jones
            17052, // Topher Grace
            17604, // Jeremy Renner
            20766, // Richard E. Grant
            22226, // Paul Rudd
            27578, // Elliot Page
            30082, // Benedict Wong
            38673, // Channing Tatum
            43667, // Kisuke Iida
            45405, // Jay Duplass
            55585, // Sam Spruell
            56446, // John Cena
            58502, // Natalie Dormer
            71070, // Amanda Seyfried
            77069, // Lenny Kravitz
            79006, // Kenji Mizuhashi
            83854, // David Dastmalchian
            100766, // Sakura Ando
            110298, // Shobana
            111640, // Munetaka Aoki
            111691, // Hidetaka Yoshioka
            121716, // Kuranosuke Sasaki
            167564, // Paul Sparks
            172069, // Chadwick Boseman
            205923, // Babs Olusanmokun
            220914, // Peta Sergeant
            457386, // Arian Moayed
            513677, // Henry Lloyd-Hughes
            543261, // Karen Gillan
            561869, // Amandla Stenberg
            986808, // Wyatt Russell
            1091324, // Rob Delaney
            1161474, // Yuki Yamada
            1477958, // Matt Lowe
            1516266, // Minami Hamabe
            1542283, // Adam Silver
            1649152, // Jacob Batalon
            1731537, // Hwang Seok-jeong
            2028037, // Tramell Tillman
            2430993, // Miou Tanaka
            2569691, // Young Mazino
            2593824, // Hwang Jae-yeol
            4019851, // Simms May
            4389435, // Sae Nagatani
            // legacy-звёзды (MVP), не вошедшие в пул:
            31, // Tom Hanks
            38, // Harrison Ford
            39, // Robin Williams
            42, // Michael Douglas
            122, // Robert De Niro
            287, // Brad Pitt
            385, // Johnny Depp
            5381, // Meryl Streep
            9226, // Tom Cruise
            2518, // Leonardo DiCaprio
        )

        /** Все упорядоченные пары (стартовый, целевой), 117 × 116 = 13,572. */
        val HARDCODED_PAIRS: List<Pair<Int, Int>> = PAIR_ACTORS.flatMap { start ->
            PAIR_ACTORS.filter { it != start }.map { start to it }
        }
    }
}
