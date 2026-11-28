package com.zurus.cinethread.domain.util

import com.zurus.cinethread.domain.FakeActorRepository
import com.zurus.cinethread.domain.FakeGraph
import com.zurus.cinethread.domain.FakeMovieRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * PairGenerator: воспроизводимость, гарантия решаемости, причины отказа
 * (docs/04_algorithms.md §3, Этап 3).
 */
class PairGeneratorTest {

    /**
     * Граф: 1 -101- 2 -102- 3 -103- 4, 3 -104- 5, 4 -105- 6, 5 -106- 7.
     * От 1: глубина 2 — {3, 5}, глубина 3 — {4, 6, 7}.
     */
    private val graph = FakeGraph()
        .edge(1, 101).edge(2, 101)
        .edge(2, 102).edge(3, 102)
        .edge(3, 103).edge(4, 103)
        .edge(3, 104).edge(5, 104)
        .edge(4, 105).edge(6, 105)
        .edge(5, 106).edge(7, 106)

    private val startPool = listOf(1, 2)

    private suspend fun run(
        seed: Long,
        config: PairGenConfig = PairGenConfig(),
        pool: List<Int> = startPool,
        budget: Budget = Budget(),
    ): PairResult {
        val g = PairGenerator(FakeActorRepository(graph), FakeMovieRepository(graph))
        val result = g.generatePair(seed, config, pool, budget)
        val value = result.getOrNull()
        assertTrue(value != null, "ожидался результат, получен сбой: $result")
        return value
    }

    @Test
    fun `same seed gives the same pair`() = runBlocking {
        val first = run(seed = 123)
        val second = run(seed = 123)
        assertEquals(first, second, "один seed — одна пара")
    }

    @Test
    fun `generated pair is guaranteed solvable`() = runBlocking {
        val ok = run(seed = 7) as? PairResult.Ok
        assertTrue(ok != null, "ожидалась Ok, получен $ok")
        assertTrue(ok.startActorId in startPool)
        assertTrue(ok.distance in 2..PairGenConfig().maxSteps)
        assertEquals(ok.distance, ok.shortestPath.size)
        assertEquals(ok.startActorId, ok.shortestPath.first().fromActorId)
        assertEquals(ok.targetActorId, ok.shortestPath.last().toActorId)
    }

    @Test
    fun `path steps are consistent`() = runBlocking {
        val ok = run(seed = 7) as? PairResult.Ok
        assertTrue(ok != null)
        val path = ok.shortestPath
        for (i in 1 until path.size) {
            assertEquals(path[i - 1].toActorId, path[i].fromActorId, "шаги обязаны стыковаться")
        }
        assertEquals(path.indices.map { it + 1 }, path.map { it.stepIndex }, "нумерация шагов с 1")
    }

    @Test
    fun `empty pool gives NO_START_CANDIDATES`() = runBlocking {
        assertEquals(PairResult.Fail(PairFailReason.NO_START_CANDIDATES), run(seed = 1, pool = emptyList()))
    }

    @Test
    fun `exhausted budget gives BUDGET_EXCEEDED`() = runBlocking {
        assertEquals(PairResult.Fail(PairFailReason.BUDGET_EXCEEDED), run(seed = 1, budget = Budget(maxRequests = 0)))
    }

    @Test
    fun `no reachable targets gives NO_VALID_PAIR`() = runBlocking {
        val lonely = FakeGraph().edge(1, 101).edge(2, 101)
        val g = PairGenerator(FakeActorRepository(lonely), FakeMovieRepository(lonely))
        val result = g.generatePair(1, PairGenConfig(), listOf(1), Budget()).getOrNull()
        assertEquals(PairResult.Fail(PairFailReason.NO_VALID_PAIR), result)
    }

    @Test
    fun `preferred depth is chosen when available`() = runBlocking {
        val ok = run(seed = 55) as? PairResult.Ok
        assertTrue(ok != null, "ожидалась Ok, получен $ok")
        val config = PairGenConfig()
        assertTrue(
            ok.distance in config.preferredMinSteps..config.preferredMaxSteps,
            "при наличии кандидатов предпочтителен диапазон ${config.preferredMinSteps}..${config.preferredMaxSteps}",
        )
    }

    @Test
    fun `trivial pair excluded by default`() = runBlocking {
        val g = FakeGraph().edge(1, 101).edge(2, 101)
        val generator = PairGenerator(FakeActorRepository(g), FakeMovieRepository(g))
        val config = PairGenConfig(minSteps = 1, maxSteps = 1, allowTrivial = false)
        val result = generator.generatePair(1, config, listOf(1), Budget()).getOrNull()
        assertEquals(PairResult.Fail(PairFailReason.NO_VALID_PAIR), result)
    }

    @Test
    fun `trivial pair allowed when requested`() = runBlocking {
        val g = FakeGraph().edge(1, 101).edge(2, 101)
        val generator = PairGenerator(FakeActorRepository(g), FakeMovieRepository(g))
        val config = PairGenConfig(minSteps = 1, maxSteps = 1, allowTrivial = true)
        val ok = generator.generatePair(1, config, listOf(1), Budget()).getOrNull() as? PairResult.Ok
        assertTrue(ok != null, "ожидалась Ok, получен $ok")
        assertEquals(1, ok.distance)
        assertEquals(1, ok.shortestPath.size)
    }

    @Test
    fun `different seeds pick different targets on same graph`() = runBlocking {
        // Один старт, три цели в предпочтительном диапазоне (4, 6, 7):
        // seeded shuffle должен дать разные выборки на разных seed.
        val targets = (1L..20L).map { seed ->
            val ok = run(seed = seed, pool = listOf(1)) as? PairResult.Ok
            assertTrue(ok != null, "seed $seed: ожидалась Ok, получен $ok")
            ok.targetActorId
        }
        assertTrue(
            targets.distinct().size >= 2,
            "разные seed — разные пары, а получили $targets",
        )
    }

    @Test
    fun `forDepth sets exact depth bounds (D5)`() {
        val config = PairGenConfig().forDepth(3)
        assertEquals(3, config.minSteps)
        assertEquals(3, config.maxSteps)
        // Остальные параметры не трогаются.
        assertEquals(PairGenConfig().startPoolSize, config.startPoolSize)
    }
}
