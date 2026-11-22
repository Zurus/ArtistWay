package com.zurus.kinosvyazi.domain.util

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeMovieRepository
import com.zurus.kinosvyazi.domain.model.ChainStep
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * BFS: кратчайший путь, слои, восстановление цепочки
 * (docs/04_algorithms.md §2, §3.3, Этап 3).
 */
class BfsTest {

    private val rng = Random(42)

    /** Граф: 1 --101-- 2 --102-- 3 (цепочка длины 2). */
    private val chainGraph = FakeGraph().edge(1, 101).edge(2, 101).edge(2, 102).edge(3, 102)

    private fun cache(graph: FakeGraph = chainGraph, budget: Budget = Budget()) =
        GenCache(FakeActorRepository(graph), FakeMovieRepository(graph), budget)

    @Test
    fun `start equals target returns distance 0`() = runBlocking {
        val result = Bfs.bfsShortestPath(1, 1, 3, cache(), Budget())
        assertEquals(BfsResult.Found(distance = 0, parents = emptyMap()), result.getOrNull())
    }

    @Test
    fun `shortest path distance and chain reconstruction`() = runBlocking {
        val budget = Budget()
        val result = Bfs.bfsShortestPath(1, 3, 3, cache(), budget)
        val found = result.getOrNull() as? BfsResult.Found
        assertTrue(found != null, "ожидался FOUND, получен $found")
        assertEquals(2, found.distance)
        assertEquals(
            listOf(
                ChainStep(1, 1, 101, 2),
                ChainStep(2, 2, 102, 3),
            ),
            Bfs.reconstructChain(1, 3, found.parents),
        )
    }

    @Test
    fun `shared movie creates direct edge without loop`() = runBlocking {
        val graph = FakeGraph().edge(1, 201).edge(2, 201)
        val result = Bfs.bfsShortestPath(1, 2, 2, cache(graph), Budget()).getOrNull()
        val found = result as? BfsResult.Found
        assertTrue(found != null, "ожидался FOUND, получен $result")
        assertEquals(1, found.distance)
    }

    @Test
    fun `no path within maxSteps returns NOT_FOUND`() = runBlocking {
        val graph = FakeGraph()
            .edge(1, 1).edge(2, 1)
            .edge(2, 2).edge(3, 2)
            .edge(3, 3).edge(4, 3)
        val result = Bfs.bfsShortestPath(1, 4, 1, cache(graph), Budget()).getOrNull()
        assertEquals(BfsResult.NotFound, result)
    }

    @Test
    fun `exhausted budget returns BUDGET_EXCEEDED`() = runBlocking {
        val budget = Budget(maxRequests = 2)
        val result = Bfs.bfsShortestPath(1, 3, 3, cache(budget = budget), budget).getOrNull()
        assertEquals(BfsResult.BudgetExceeded, result)
    }

    @Test
    fun `repository failure propagates as error`() = runBlocking {
        val graph = FakeGraph().edge(1, 101).edge(2, 101)
        val actorRepository = FakeActorRepository(graph, failWith = AppError.Network)
        val cache = GenCache(actorRepository, FakeMovieRepository(graph), Budget())
        val result = Bfs.bfsShortestPath(1, 2, 2, cache, Budget())
        assertTrue(result is Result.Failure)
        assertEquals(AppError.Network, (result as Result.Failure).error)
    }

    @Test
    fun `boundedBfsLayers collects layers by depth`() = runBlocking {
        val budget = Budget()
        val result = Bfs.boundedBfsLayers(1, 2, PairGenConfig(), rng, cache(), budget).getOrNull()
        val layers = result as? LayersResult
        assertTrue(layers != null && layers.reason == BfsReason.OK, "ожидался OK, получен $result")
        assertEquals(listOf(2), layers.byDepth[1])
        assertEquals(listOf(3), layers.byDepth[2])
    }

    @Test
    fun `boundedBfsLayers stops at frontier collapse`() = runBlocking {
        val graph = FakeGraph().edge(1, 301).edge(2, 301)
        val result = Bfs.boundedBfsLayers(1, 5, PairGenConfig(), rng, cache(graph), Budget()).getOrNull()
        val layers = result as? LayersResult
        assertTrue(layers != null && layers.reason == BfsReason.OK, "ожидался OK, получен $result")
        assertEquals(listOf(2), layers.byDepth[1], "слой 1 достигнут")
        assertNull(layers.byDepth[2], "фронт коллапсировал — слоя 2 нет")
    }

    @Test
    fun `boundedBfsLayers budget exhausted`() = runBlocking {
        val result = Bfs.boundedBfsLayers(1, 3, PairGenConfig(), rng, cache(), Budget(maxRequests = 0)).getOrNull()
        assertEquals(LayersResult.budgetExceeded(), result)
    }

    @Test
    fun `reconstructChain empty when start equals target`() {
        assertTrue(Bfs.reconstructChain(5, 5, emptyMap()).isEmpty())
    }

    @Test
    fun `reconstructChain returns empty on broken parents`() {
        assertTrue(
            Bfs.reconstructChain(
                1, 2, mapOf(2 to ParentLink(movieId = 101, prevActorId = 99)),
            ).isEmpty(),
        )
    }

    @Test
    fun `limitCandidates keeps all below limit`() {
        assertEquals(listOf(1, 2), Bfs.limitCandidates(listOf(1, 2), limit = 5, rng = rng))
    }

    @Test
    fun `limitCandidates takes deterministic subset by seed`() {
        val items = (1..20).toList()
        val first = Bfs.limitCandidates(items, limit = 5, rng = Random(1))
        val second = Bfs.limitCandidates(items, limit = 5, rng = Random(1))
        val other = Bfs.limitCandidates(items, limit = 5, rng = Random(2))
        assertEquals(5, first.size)
        assertEquals(first, second, "один seed — один результат")
        assertTrue(first != other, "разные seed — разные подмножества (на 20 элементах)")
    }
}
