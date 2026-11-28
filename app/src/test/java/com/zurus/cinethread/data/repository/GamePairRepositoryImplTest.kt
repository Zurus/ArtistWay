package com.zurus.cinethread.data.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.FakeActorRepository
import com.zurus.cinethread.domain.FakeGraph
import com.zurus.cinethread.domain.FakeMovieRepository
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.util.Budget
import com.zurus.cinethread.domain.util.GenCache
import com.zurus.cinethread.domain.util.PairGenConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты зашитых пар [GamePairRepositoryImpl] (MVP: `HARDCODED_PAIRS`; D1:
 * двухуровневый бюджет; D5: глубина 2 — пересечение ко-актёров
 * [DepthTwoPairSearch], глубины 3–4 — целевой BFS).
 */
class GamePairRepositoryImplTest {

    private val config = PairGenConfig()

    private fun repo(
        graph: FakeGraph,
        actorFail: AppError? = null,
        // D5: на d=2 пара стоит 2 запроса (фильмографии обоих актёров),
        // поэтому дефолт на все 13,572 пары: 13,572 × 2 < 30,000.
        budget: Budget = Budget(maxRequests = 30_000, maxNodesVisited = 100_000, maxWallClockMs = 60_000),
    ): GamePairRepositoryImpl {
        return GamePairRepositoryImpl(
            FakeActorRepository(graph, failWith = actorFail),
            FakeMovieRepository(graph),
            config,
            { budget },
        )
    }

    @Test
    fun `generates solvable hardcoded pair with shortest path`() = runTest {
        // 31 (Hanks) <-> 38 (Ford), расстояние 2, через общего ко-актёра 900.
        val graph = FakeGraph()
            .edge(31, 101).edge(900, 101)
            .edge(900, 102).edge(38, 102)

        val result = repo(graph).generatePair(seed = 42L)

        val pair = (result as Result.Success).value
        assertEquals(
            "оба конца — звёзды 31/38",
            setOf(31, 38),
            setOf(pair.startActor.tmdbId, pair.targetActor.tmdbId),
        )
        assertEquals("путь из 2 шагов", 2, pair.shortestPath.size)
        assertEquals(
            "первый шаг от стартового актёра",
            pair.startActor.tmdbId,
            pair.shortestPath.first().fromActorId,
        )
        assertEquals(
            "последний шаг к целевому актёру",
            pair.targetActor.tmdbId,
            pair.shortestPath.last().toActorId,
        )
    }

    @Test
    fun `same seed gives the same pair`() = runTest {
        // Две решаемые пары: (31, 38) и (39, 122) — shuffle по seed
        // выбирает одну из них детерминированно.
        val graph = FakeGraph()
            .edge(31, 101).edge(900, 101)
            .edge(900, 102).edge(38, 102)
            .edge(39, 201).edge(901, 201)
            .edge(901, 202).edge(122, 202)

        val first = (repo(graph).generatePair(seed = 7L) as Result.Success).value
        val second = (repo(graph).generatePair(seed = 7L) as Result.Success).value

        assertEquals(
            "один seed -> одна пара",
            first.startActor.tmdbId to first.targetActor.tmdbId,
            second.startActor.tmdbId to second.targetActor.tmdbId,
        )
    }

    @Test
    fun `trivial pair with one shared movie is skipped`() = runTest {
        // Единственная связь: 31 и 38 в одном фильме (d = 1 < minSteps = 2).
        val graph = FakeGraph().edge(31, 101).edge(38, 101)

        val result = repo(graph).generatePair(seed = 42L)

        assertTrue("ожидается NoValidPair, получено $result", result is Result.Failure)
        assertEquals(
            "пара d=1 отбрасывается (allowTrivial=false)",
            PairGenerationError.NoValidPair,
            (result as Result.Failure).error,
        )
    }

    @Test
    fun `empty graph returns NoValidPair`() = runTest {
        val result = repo(FakeGraph()).generatePair(seed = 1L)

        assertTrue("ожидается NoValidPair, получено $result", result is Result.Failure)
        assertEquals(
            PairGenerationError.NoValidPair,
            (result as Result.Failure).error,
        )
    }

    @Test
    fun `budget exhaustion returns BudgetExceeded`() = runTest {
        // У 31 (Hanks) 3 фильма: с лимитом в 2 запроса бюджет
        // исчерпывается либо в BFS, либо на границе цикла.
        val graph = FakeGraph()
            .edge(31, 101).edge(901, 101)
            .edge(31, 102).edge(902, 102)
            .edge(31, 103).edge(903, 103)
        val budget = Budget(maxRequests = 2, maxNodesVisited = 10_000, maxWallClockMs = 60_000)

        val result = repo(graph, budget = budget).generatePair(seed = 42L)

        assertTrue(
            "ожидается BudgetExceeded, получено $result",
            result is Result.Failure,
        )
        assertTrue(
            "исчерпание бюджета — валидное состояние",
            (result as Result.Failure).error is PairGenerationError.BudgetExceeded,
        )
    }

    @Test
    fun `data error is propagated as DataError`() = runTest {
        val fail = AppError.DataInvalid("boom")
        val graph = FakeGraph().edge(31, 101).edge(38, 101)

        val result = repo(graph, actorFail = fail).generatePair(seed = 42L)

        assertTrue("ожидается DataError, получено $result", result is Result.Failure)
        assertEquals(
            PairGenerationError.DataError(fail),
            (result as Result.Failure).error,
        )
    }

    @Test
    fun `expensive pair is skipped, generation survives (D1, d=3)`() = runTest {
        // D1: у актёра 38 «хаб»-фильм на 1600 ко-актёров — BFS от него
        // бьёт в per-pair лимит узлов (1500). В старом коде
        // (break на BfsResult.BudgetExceeded) вся генерация умирала на
        // первой такой паре; теперь — пробуем следующую. seed=109 =>
        // первая пара в shuffle — (38, 543261) (дорогая, d=3:
        // 38→1000→7000→404→543261).
        val graph = FakeGraph()
            .edge(31, 101).edge(900, 101)
            .edge(900, 202).edge(122, 202)
            .edge(122, 303).edge(2231, 303)
            .edge(38, 1000)
            .edge(7000, 404).edge(543261, 404)
        repeat(1600) {
            graph.edge(7000 + it, 1000)
        }
        val budget = Budget(maxRequests = 5000, maxNodesVisited = 100_000, maxWallClockMs = 60_000)

        val result = repo(graph, budget = budget).generatePair(seed = 109L, depth = 3)

        val pair = (result as Result.Success).value
        assertEquals(
            "решаемая пара (31, 2231) найдена, несмотря на дорогую",
            setOf(31, 2231),
            setOf(pair.startActor.tmdbId, pair.targetActor.tmdbId),
        )
        assertEquals("путь из 3 шагов", 3, pair.shortestPath.size)
    }

    // ===== D5: глубина пары (docs/debugging.md) =====

    @Test
    fun `depth 2 pair found via co-actor intersection (D5)`() = runTest {
        // 31 (Hanks) <-> 38 (Ford), расстояние 2, через общего ко-актёра 900:
        // путь A→101→900→102→B собирается из пересечения, без BFS.
        val graph = FakeGraph()
            .edge(31, 101).edge(900, 101)
            .edge(900, 102).edge(38, 102)

        val result = repo(graph).generatePair(seed = 42L, depth = 2)

        val pair = (result as Result.Success).value
        assertEquals(setOf(31, 38), setOf(pair.startActor.tmdbId, pair.targetActor.tmdbId))
        val path = pair.shortestPath
        assertEquals("путь из 2 шагов", 2, path.size)
        assertEquals(pair.startActor.tmdbId, path[0].fromActorId)
        assertEquals("общий ко-актёр на первом шаге", 900, path[0].toActorId)
        assertEquals(900, path[1].fromActorId)
        assertEquals(pair.targetActor.tmdbId, path[1].toActorId)
    }

    @Test
    fun `d1 pair is not disguised as d2 (D5)`() = runTest {
        // 31 и 38 делят фильм 101 (d = 1) и дополнительно связаны «за 2 шага»
        // через 900 — пара тривиальная и не выдаётся на глубине 2.
        val graph = FakeGraph()
            .edge(31, 101).edge(38, 101)
            .edge(31, 103).edge(900, 103)
            .edge(900, 104).edge(38, 104)

        val result = repo(graph).generatePair(seed = 42L, depth = 2)

        assertTrue("ожидается NoValidPair, получено $result", result is Result.Failure)
        assertEquals(
            "пара с общим фильмом не выдаётся на глубине 2",
            PairGenerationError.NoValidPair,
            (result as Result.Failure).error,
        )
    }

    @Test
    fun `depth 3 pair is found via BFS with exact depth (D5)`() = runTest {
        // Цепь 31 -101- 900 -202- 122 -303- 2231: расстояние ровно 3.
        val graph = FakeGraph()
            .edge(31, 101).edge(900, 101)
            .edge(900, 202).edge(122, 202)
            .edge(122, 303).edge(2231, 303)

        val result = repo(graph).generatePair(seed = 42L, depth = 3)

        val pair = (result as Result.Success).value
        assertEquals(setOf(31, 2231), setOf(pair.startActor.tmdbId, pair.targetActor.tmdbId))
        assertEquals("цепочка из 3 шагов", 3, pair.shortestPath.size)
    }

    @Test
    fun `d2 search does not expand the hub cast (D5)`() = runTest {
        // У 38 есть «хаб»-фильм 1000 (1600 ко-актёров; ID из диапазона
        // 900_000_000 — не пересекаются с пулом PAIR_ACTORS, чтобы хаб
        // не тянул посторонние пары) и фильм 101 (с 900);
        // 900 также в 301 с 31. d=2: общий ко-актёр 900 найден на первом
        // чередовании фильмов — состав хаба никогда не загружается.
        val graph = FakeGraph()
            .edge(38, 101).edge(900, 101)
            .edge(900, 301).edge(31, 301)
            .edge(38, 1000)
        repeat(1600) {
            graph.edge(900_000_000 + it, 1000)
        }
        val movieRepo = FakeMovieRepository(graph)
        val repo = GamePairRepositoryImpl(
            FakeActorRepository(graph),
            movieRepo,
            config,
            { Budget(maxRequests = 30_000, maxNodesVisited = 100_000, maxWallClockMs = 60_000) },
        )

        val result = repo.generatePair(seed = 42L, depth = 2)

        val pair = (result as Result.Success).value
        assertEquals(setOf(31, 38), setOf(pair.startActor.tmdbId, pair.targetActor.tmdbId))
        assertEquals("загружены только составы 101 и 301", 2, movieRepo.castCalls)
    }

    @Test
    fun `d2 search reuses filmography data of the start actor (D5)`() = runTest {
        val graph = FakeGraph()
            .edge(1, 101).edge(500, 101)
            .edge(1, 103).edge(600, 103)
            .edge(2, 201).edge(700, 201)
            .edge(3, 301).edge(600, 301)
        val actorRepo = FakeActorRepository(graph)
        val movieRepo = FakeMovieRepository(graph)
        val cache = GenCache(
            actorRepo,
            movieRepo,
            Budget(maxRequests = 1000, maxNodesVisited = 10_000, maxWallClockMs = 60_000),
        )
        val pairBudget = Budget(
            maxRequests = Int.MAX_VALUE,
            maxNodesVisited = 10_000,
            maxWallClockMs = 60_000,
        )

        val first = DepthTwoPairSearch.find(1, 2, cache, pairBudget)
        assertTrue("у (1, 2) нет общего ко-актёра", first is DepthTwoResult.NoCommon)
        val callsAfterFirst = actorRepo.movieCalls

        val second = DepthTwoPairSearch.find(1, 3, cache, pairBudget)

        assertEquals("общий ко-актёр 600 через 103/301", DepthTwoResult.Found(103, 600, 301), second)
        assertEquals(
            "фильмография актёра 1 не запрашивается заново (GenCache)",
            callsAfterFirst + 1,
            actorRepo.movieCalls,
        )
    }

    @Test
    fun `d2 search respects pair and outer budgets (D5)`() = runTest {
        val graph = FakeGraph()
            .edge(1, 101).edge(500, 101)
            .edge(2, 201).edge(700, 201)
        val cache = GenCache(
            FakeActorRepository(graph),
            FakeMovieRepository(graph),
            Budget(maxRequests = 1000, maxNodesVisited = 10_000, maxWallClockMs = 60_000),
        )
        // 3 узла — не хватает на раскрытие состава одного фильма.
        val tinyPairBudget = Budget(
            maxRequests = Int.MAX_VALUE,
            maxNodesVisited = 3,
            maxWallClockMs = 60_000,
        )

        val pairResult = DepthTwoPairSearch.find(1, 2, cache, tinyPairBudget)
        assertTrue("пер-пара лимит защищает поиск", pairResult is DepthTwoResult.PairBudgetExceeded)

        val exhaustedCache = GenCache(
            FakeActorRepository(graph),
            FakeMovieRepository(graph),
            Budget(maxRequests = 0, maxNodesVisited = 10_000, maxWallClockMs = 60_000),
        )
        val outerResult = DepthTwoPairSearch.find(
            1,
            2,
            exhaustedCache,
            Budget(maxRequests = Int.MAX_VALUE, maxNodesVisited = 10_000, maxWallClockMs = 60_000),
        )
        assertTrue("суммарный бюджет останавливает генерацию", outerResult is DepthTwoResult.OuterBudgetExceeded)
    }

    @Test
    fun `unsupported depth returns DataError (D5)`() = runTest {
        val result = repo(FakeGraph()).generatePair(seed = 1L, depth = 1)
        assertTrue("ожидается DataError, получено $result", result is Result.Failure)
        assertTrue((result as Result.Failure).error is PairGenerationError.DataError)
    }

    @Test
    fun `pool has 117 unique actors (D2)`() {
        assertEquals(
            "пул = 107 (generate_start_pool.py) + 10 legacy-звёзд",
            117,
            GamePairRepositoryImpl.PAIR_ACTORS.size,
        )
        assertEquals(
            "дублей ID в пуле нет",
            GamePairRepositoryImpl.PAIR_ACTORS.size,
            GamePairRepositoryImpl.PAIR_ACTORS.toSet().size,
        )
    }

    @Test
    fun `pairs are N*(N-1), no self-pairs (D2)`() {
        val pairs = GamePairRepositoryImpl.HARDCODED_PAIRS
        assertEquals("117*116 = 13,572 пар", 117 * 116, pairs.size)
        assertTrue("само-пар нет", pairs.none { it.first == it.second })
    }

    @Test
    fun `RDJ duplicate removed, pool ID kept (D2)`() {
        assertTrue(
            "RDJ: пуловый ID 3223 на месте (принят на доверии, проверить по VPN)",
            GamePairRepositoryImpl.PAIR_ACTORS.contains(3223),
        )
        assertTrue(
            "RDJ: старый ID 2871 удалён (дубль = пара «актёр с самим собой»)",
            !GamePairRepositoryImpl.PAIR_ACTORS.contains(2871),
        )
        assertTrue("legacy-звезда Tom Hanks сохранена", GamePairRepositoryImpl.PAIR_ACTORS.contains(31))
    }
}
