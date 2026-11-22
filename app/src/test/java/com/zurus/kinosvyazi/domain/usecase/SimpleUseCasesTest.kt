package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeMovieRepository
import com.zurus.kinosvyazi.domain.sessionWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Простые UseCase: GetActorMovies, GetMovieCast, GetGameSession (Этап 3).
 */
class SimpleUseCasesTest {

    private val graph = FakeGraph()
        .edge(1, 101).edge(2, 101)
        .edge(2, 102).edge(3, 102)

    @Test
    fun `get actor movies passes through to repository`() = runBlocking {
        val useCase = GetActorMoviesUseCase(FakeActorRepository(graph))
        val movies = useCase(1).getOrNull()
        assertTrue(movies != null)
        assertEquals(listOf(101), movies.map { it.tmdbId })
    }

    @Test
    fun `get actor movies failure passes through`() = runBlocking {
        val useCase = GetActorMoviesUseCase(FakeActorRepository(graph, failWith = AppError.Network))
        val result = useCase(1)
        assertTrue(result is Result.Failure)
        assertEquals(AppError.Network, (result as Result.Failure).error)
    }

    @Test
    fun `get movie cast passes through to repository`() = runBlocking {
        val useCase = GetMovieCastUseCase(FakeMovieRepository(graph))
        val cast = useCase(101).getOrNull()
        assertTrue(cast != null)
        assertEquals(listOf(1, 2), cast.map { it.tmdbId })
    }

    @Test
    fun `get movie cast failure passes through`() = runBlocking {
        val useCase = GetMovieCastUseCase(FakeMovieRepository(graph, failWith = AppError.Offline))
        val result = useCase(101)
        assertTrue(result is Result.Failure)
        assertEquals(AppError.Offline, (result as Result.Failure).error)
    }

    @Test
    fun `get game session returns active session`() = runBlocking {
        val repository = FakeGameSessionRepository()
        val session = sessionWith(emptyList(), currentActorId = 1)
        repository.activeSession = session
        val useCase = GetGameSessionUseCase(repository)
        assertEquals(session, useCase())
    }

    @Test
    fun `get game session returns null when none active`() = runBlocking {
        val useCase = GetGameSessionUseCase(FakeGameSessionRepository())
        assertNull(useCase())
    }
}
