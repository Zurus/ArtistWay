package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.FakeGamePairRepository
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.actor
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.repository.PairGenerationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * StartGameUseCase: атомарный запуск партии (Этап 3).
 */
class StartGameUseCaseTest {

    private val pair = GamePair(
        startActor = actor(1),
        targetActor = actor(3),
        shortestPath = listOf(
            ChainStep(1, 1, 101, 2),
            ChainStep(2, 2, 102, 3),
        ),
        seed = 99L,
    )

    private fun useCase(
        pairRepository: FakeGamePairRepository,
        sessionRepository: FakeGameSessionRepository = FakeGameSessionRepository(),
    ) = StartGameUseCase(pairRepository, sessionRepository)

    @Test
    fun `success starts session with guaranteed path and seed`() = runBlocking {
        val pairRepository = FakeGamePairRepository(Result.Success(pair))
        val sessionRepository = FakeGameSessionRepository()
        val useCase = useCase(pairRepository, sessionRepository)
        val result = useCase(seed = 99L)
        val session = result.getOrNull()
        assertTrue(session != null, "ожидалась партия, получен сбой: $result")
        assertEquals(pair.startActor, session.startActor)
        assertEquals(pair.targetActor, session.targetActor)
        assertEquals(pair.shortestPath, session.shortestPath)
        assertEquals(99L, session.generationSeed)
        assertEquals(1, sessionRepository.startedPairs.size)
        assertEquals(99L, pairRepository.lastSeed, "seed обязан передаваться в репозиторий")
    }

    @Test
    fun `null seed is passed through`() = runBlocking {
        val pairRepository = FakeGamePairRepository(Result.Success(pair))
        val useCase = useCase(pairRepository)
        useCase()
        assertEquals(null, pairRepository.lastSeed)
    }

    @Test
    fun `budget exceeded is surfaced`() = runBlocking {
        val useCase = useCase(
            FakeGamePairRepository(
                Result.failure(PairGenerationError.BudgetExceeded("requests=300/300")),
            ),
        )
        val result = useCase()
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is PairGenerationError.BudgetExceeded)
    }

    @Test
    fun `no valid pair is surfaced`() = runBlocking {
        val useCase = useCase(FakeGamePairRepository(Result.failure(PairGenerationError.NoValidPair)))
        val result = useCase()
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.NoValidPair, (result as Result.Failure).error)
    }

    @Test
    fun `data error is surfaced`() = runBlocking {
        val useCase = useCase(
            FakeGamePairRepository(Result.failure(PairGenerationError.DataError(AppError.Network))),
        )
        val result = useCase()
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.DataError(AppError.Network), (result as Result.Failure).error)
    }

    @Test
    fun `session start failure maps to data error`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository().apply { startFail = AppError.Server }
        val useCase = useCase(FakeGamePairRepository(Result.Success(pair)), sessionRepository)
        val result = useCase()
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.DataError(AppError.Server), (result as Result.Failure).error)
    }
}
