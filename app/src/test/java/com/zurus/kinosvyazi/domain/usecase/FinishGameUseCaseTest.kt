package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Clock
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.sessionBetween
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GameResultReason
import com.zurus.kinosvyazi.domain.model.GameSessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * FinishGameUseCase: итог партии (docs/01_concept.md §6.19, Этап 3).
 */
class FinishGameUseCaseTest {

    private val clock = Clock { 42L }

    private val session = sessionBetween(
        startId = 1,
        targetId = 3,
        chain = listOf(
            ChainStep(1, 1, 101, 2),
            ChainStep(2, 2, 102, 3),
        ),
    )

    private fun useCase(sessionRepository: FakeGameSessionRepository = FakeGameSessionRepository()) =
        FinishGameUseCase(sessionRepository, clock)

    @Test
    fun `win result carries chain and guaranteed distance`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository()
        val result = useCase(sessionRepository)(session, GameResultReason.WIN)
        val gameResult = result.getOrNull()
        assertTrue(gameResult != null, "ожидался итог, получен $result")
        assertEquals(GameResultReason.WIN, gameResult.reason)
        assertEquals(2, gameResult.chain.size)
        assertEquals(2, gameResult.stepCount)
        assertEquals(2, gameResult.guaranteedDistance, "длина гарантированного решения")
        assertEquals(0L, gameResult.startedAtMillis)
        assertEquals(42L, gameResult.finishedAtMillis)
        assertTrue(gameResult.isWin)
        assertEquals(1, sessionRepository.finished.size)
        assertEquals(GameSessionStatus.WON, sessionRepository.activeSession?.status)
    }

    @Test
    fun `surrender reason and status`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository()
        val result = useCase(sessionRepository)(session, GameResultReason.SURRENDER)
        assertEquals(GameResultReason.SURRENDER, result.getOrNull()?.reason)
        assertEquals(GameSessionStatus.SURRENDERED, sessionRepository.activeSession?.status)
    }

    @Test
    fun `dead end reason and status`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository()
        val result = useCase(sessionRepository)(session, GameResultReason.DEAD_END)
        assertEquals(GameResultReason.DEAD_END, result.getOrNull()?.reason)
        assertEquals(GameSessionStatus.DEAD_END, sessionRepository.activeSession?.status)
    }

    @Test
    fun `error reason and status`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository()
        val result = useCase(sessionRepository)(session, GameResultReason.ERROR)
        assertEquals(GameResultReason.ERROR, result.getOrNull()?.reason)
        assertEquals(GameSessionStatus.ERROR, sessionRepository.activeSession?.status)
    }

    @Test
    fun `new game reason maps to surrendered status`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository()
        val result = useCase(sessionRepository)(session, GameResultReason.NEW_GAME)
        assertEquals(GameResultReason.NEW_GAME, result.getOrNull()?.reason)
        assertEquals(GameSessionStatus.SURRENDERED, sessionRepository.activeSession?.status)
    }

    @Test
    fun `repository failure propagates`() = runBlocking {
        val sessionRepository = FakeGameSessionRepository().apply { finishFail = AppError.Server }
        val result = useCase(sessionRepository)(session, GameResultReason.WIN)
        assertTrue(result is Result.Failure)
        assertEquals(AppError.Server, (result as Result.Failure).error)
        assertEquals(0, sessionRepository.finished.size, "итог не сохраняется при провале")
    }
}
