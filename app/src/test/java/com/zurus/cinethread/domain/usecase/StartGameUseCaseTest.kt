package com.zurus.cinethread.domain.usecase

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Clock
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.FakePairSource
import com.zurus.cinethread.domain.FakeGameSessionRepository
import com.zurus.cinethread.domain.actor
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.model.GameResultReason
import com.zurus.cinethread.domain.model.GameSessionStatus
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.sessionWith
import com.zurus.cinethread.domain.util.PairGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * StartGameUseCase: атомарный запуск партии (Этап 3).
 *
 * D8 (docs/debugging.md): use case не зависит от конкретного источника
 * пары — тесты прогоняют его с [FakePairSource] (роль онлайн/банка/auto
 * определяет DI по `BuildConfig.PAIR_SOURCE`; поведение источников —
 * тесты `data/pairsource`).
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
        pairSource: FakePairSource,
        sessionRepository: FakeGameSessionRepository = FakeGameSessionRepository(),
    ) = StartGameUseCase(
        pairSource,
        PairGenConfig(),
        sessionRepository,
        FinishGameUseCase(sessionRepository, Clock { 5000L }),
    )

    @Test
    fun `success starts session with guaranteed path and seed`() = runBlocking {
        val pairSource = FakePairSource(Result.Success(pair))
        val sessionRepository = FakeGameSessionRepository()
        val useCase = useCase(pairSource, sessionRepository)
        val result = useCase(seed = 99L)
        val session = result.getOrNull()
        assertTrue(session != null, "ожидалась партия, получен сбой: $result")
        assertEquals(pair.startActor, session.startActor)
        assertEquals(pair.targetActor, session.targetActor)
        assertEquals(pair.shortestPath, session.shortestPath)
        assertEquals(99L, session.generationSeed)
        assertEquals(1, sessionRepository.startedPairs.size)
        assertEquals(99L, pairSource.lastSeed, "seed обязан передаваться в источник")
    }

    @Test
    fun `null seed is passed through`() = runBlocking {
        val pairSource = FakePairSource(Result.Success(pair))
        val useCase = useCase(pairSource)
        useCase()
        assertEquals(null, pairSource.lastSeed)
    }

    @Test
    fun `selected depth is passed to pair source as exact-depth config (D5, D8)`() = runBlocking {
        val pairSource = FakePairSource(Result.Success(pair))
        val useCase = useCase(pairSource)
        useCase(seed = 99L, depth = 3)
        assertEquals(3, pairSource.lastDepth, "глубина обязана передаваться в источник")
    }

    @Test
    fun `default depth is 2 (D5)`() = runBlocking {
        val pairSource = FakePairSource(Result.Success(pair))
        val useCase = useCase(pairSource)
        useCase()
        assertEquals(2, pairSource.lastDepth)
    }

    @Test
    fun `unsupported depth is rejected before the source (D5)`() = runBlocking {
        val pairSource = FakePairSource(Result.Success(pair))
        val useCase = useCase(pairSource)
        val result = useCase(depth = 7)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is PairGenerationError.DataError)
        assertEquals(0, pairSource.pickCount, "источник не должен вызываться")
    }

    @Test
    fun `budget exceeded is surfaced`() = runBlocking {
        val useCase = useCase(
            FakePairSource(
                Result.failure(PairGenerationError.BudgetExceeded("requests=300/300")),
            ),
        )
        val result = useCase()
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is PairGenerationError.BudgetExceeded)
    }

    @Test
    fun `no valid pair is surfaced`() = runBlocking {
        val useCase = useCase(FakePairSource(Result.failure(PairGenerationError.NoValidPair)))
        val result = useCase()
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.NoValidPair, (result as Result.Failure).error)
    }

    @Test
    fun `data error is surfaced`() = runBlocking {
        val useCase = useCase(
            FakePairSource(Result.failure(PairGenerationError.DataError(AppError.Network))),
        )
        val result = useCase()
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.DataError(AppError.Network), (result as Result.Failure).error)
    }

    @Test
    fun `session start failure maps to data error`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository().apply { startFail = AppError.Server }
        val useCase = useCase(FakePairSource(Result.Success(pair)), sessionRepository)
        val result = useCase()
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.DataError(AppError.Server), (result as Result.Failure).error)
    }

    @Test
    fun `previous active session is closed as new game`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository()
        val previous = sessionWith(chain = listOf(ChainStep(1, 1, 101, 2)), currentActorId = 2)
        sessionRepository.sessions[previous.sessionId] = previous
        sessionRepository.activeSession = previous

        val useCase = useCase(FakePairSource(Result.Success(pair)), sessionRepository)
        val session = useCase().getOrNull()

        assertTrue(session != null, "ожидалась новая партия")
        assertNotEquals(previous.sessionId, session.sessionId)
        // docs/01_concept.md §6.19: старая партия завершается с причиной NEW_GAME.
        assertEquals(GameResultReason.NEW_GAME, sessionRepository.finished.last().reason)
        assertEquals(GameSessionStatus.SURRENDERED, sessionRepository.sessions[previous.sessionId]!!.status)
    }

    @Test
    fun `generation failure keeps previous session active`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository()
        val previous = sessionWith(chain = listOf(ChainStep(1, 1, 101, 2)), currentActorId = 2)
        sessionRepository.sessions[previous.sessionId] = previous
        sessionRepository.activeSession = previous

        val useCase = useCase(
            FakePairSource(Result.failure(PairGenerationError.NoValidPair)),
            sessionRepository,
        )
        useCase()

        // docs/01_concept.md §6.17: при ошибке генерации старая партия не теряется.
        assertEquals(GameSessionStatus.IN_PROGRESS, sessionRepository.sessions[previous.sessionId]!!.status)
        assertEquals(previous, sessionRepository.activeSession)
        assertTrue(sessionRepository.finished.isEmpty())
    }
}
