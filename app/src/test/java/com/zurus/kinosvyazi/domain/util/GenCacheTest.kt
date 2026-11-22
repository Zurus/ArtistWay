package com.zurus.kinosvyazi.domain.util

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeMovieRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * GenCache: локальная мемоизация и учёт бюджета
 * (docs/04_algorithms.md §5.2, §5.4, Этап 3).
 */
class GenCacheTest {

    private val graph = FakeGraph().edge(1, 101).edge(2, 101)

    @Test
    fun `second call is served from local cache`() = runBlocking {
        val actorRepository = FakeActorRepository(graph)
        val movieRepository = FakeMovieRepository(graph)
        val cache = GenCache(actorRepository, movieRepository, Budget())
        cache.actorMovieIds(1)
        cache.actorMovieIds(1)
        cache.movieCastIds(101)
        cache.movieCastIds(101)
        assertEquals(1, actorRepository.movieCalls, "фильмография: 1 реальный запрос")
        assertEquals(1, movieRepository.castCalls, "состав: 1 реальный запрос")
    }

    @Test
    fun `budget counts only real repository calls`() = runBlocking {
        val budget = Budget()
        val cache = GenCache(FakeActorRepository(graph), FakeMovieRepository(graph), budget)
        cache.actorMovieIds(1)
        cache.actorMovieIds(1)
        cache.actorMovieIds(2)
        cache.movieCastIds(101)
        cache.movieCastIds(101)
        assertEquals(3, budget.requestsUsed, "попадания в кэш бюджет не растят")
    }

    @Test
    fun `exhausted budget returns BudgetExceeded failure without request`() = runBlocking {
        val actorRepository = FakeActorRepository(graph)
        val budget = Budget(maxRequests = 0)
        val cache = GenCache(actorRepository, FakeMovieRepository(graph), budget)
        val result = cache.actorMovieIds(1)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.BudgetExceeded)
        assertEquals(0, actorRepository.movieCalls, "запрос к репозиторию не выполнялся")
    }

    @Test
    fun `repository failure is not cached and retry succeeds`() = runBlocking {
        val actorRepository = FakeActorRepository(graph, failWith = AppError.Server)
        val cache = GenCache(actorRepository, FakeMovieRepository(graph), Budget())
        val first = cache.actorMovieIds(1)
        assertTrue(first is Result.Failure)
        actorRepository.failWith = null
        val second = cache.actorMovieIds(1)
        assertTrue(second is Result.Success)
        assertEquals(2, actorRepository.movieCalls, "повтор после ошибки идёт в репозиторий")
    }

    @Test
    fun `cache values match graph`() = runBlocking {
        val cache = GenCache(FakeActorRepository(graph), FakeMovieRepository(graph), Budget())
        assertEquals(listOf(101), cache.actorMovieIds(1).getOrNull())
        assertEquals(listOf(1, 2), cache.movieCastIds(101).getOrNull())
    }
}
