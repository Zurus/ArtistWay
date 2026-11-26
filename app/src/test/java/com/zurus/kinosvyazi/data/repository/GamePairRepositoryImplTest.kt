package com.zurus.kinosvyazi.data.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeMovieRepository
import com.zurus.kinosvyazi.domain.repository.PairGenerationError
import com.zurus.kinosvyazi.domain.util.Budget
import com.zurus.kinosvyazi.domain.util.PairGenConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты зашитых пар [GamePairRepositoryImpl] (MVP: `HARDCODED_PAIRS`,
 * целевой BFS на каждую пару; D1: двухуровневый бюджет — дорогая пара
 * пропускается, суммарное исчерпание — `BudgetExceeded`).
 */
class GamePairRepositoryImplTest {

    private val config = PairGenConfig()

    private fun repo(
        graph: FakeGraph,
        actorFail: AppError? = null,
        budget: Budget = Budget(maxRequests = 1000, maxNodesVisited = 10_000, maxWallClockMs = 60_000),
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
    fun `expensive pair is skipped, generation survives (D1)`() = runTest {
        // D1: у актёра 38 «хаб»-фильм на 1600 ко-актёров — BFS от него
        // бьёт в per-pair лимит узлов (1500). В старом коде
        // (break на BfsResult.BudgetExceeded) вся генерация умирала на
        // первой такой паре; теперь — пробуем следующую. seed=109 =>
        // первая пара в shuffle — (38, 543261) (дорогая).
        val graph = FakeGraph()
            .edge(31, 101).edge(900, 101)
            .edge(900, 202).edge(122, 202)
        repeat(1600) {
            graph.edge(38, 1000).edge(7000 + it, 1000)
        }
        val budget = Budget(maxRequests = 5000, maxNodesVisited = 100_000, maxWallClockMs = 60_000)

        val result = repo(graph, budget = budget).generatePair(seed = 109L)

        val pair = (result as Result.Success).value
        assertEquals(
            "решаемая пара (31, 122) найдена, несмотря на дорогую",
            setOf(31, 122),
            setOf(pair.startActor.tmdbId, pair.targetActor.tmdbId),
        )
        assertEquals("путь из 2 шагов", 2, pair.shortestPath.size)
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
