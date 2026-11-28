package com.zurus.cinethread.data.pairsource

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.FakeGamePairRepository
import com.zurus.cinethread.domain.actor
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.util.PairGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * D8 (docs/debugging.md): OnlinePairSource — адаптер над
 * GamePairRepository: поведение «как сейчас» (режим `online`),
 * глубина — из конфига точной глубины.
 */
class OnlinePairSourceTest {

    private val pair = GamePair(
        startActor = actor(1),
        targetActor = actor(3),
        shortestPath = listOf(ChainStep(1, 1, 101, 3)),
        seed = 7L,
    )

    @Test
    fun `delegates to repository with depth from config and seed`() = runTest {
        val repo = FakeGamePairRepository(Result.Success(pair))
        val source = OnlinePairSource(repo)
        val result = source.pick(PairGenConfig().forDepth(4), seed = 7L)
        assertTrue(result is Result.Success)
        assertEquals(pair, (result as Result.Success).value)
        assertEquals(4, repo.lastDepth, "глубина = config.maxSteps")
        assertEquals(7L, repo.lastSeed)
    }

    @Test
    fun `repository failure passes through unchanged`() = runTest {
        val repo = FakeGamePairRepository(
            Result.failure(PairGenerationError.DataError(AppError.Network)),
        )
        val source = OnlinePairSource(repo)
        val result = source.pick(PairGenConfig().forDepth(2), seed = null)
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.DataError(AppError.Network), (result as Result.Failure).error)
        assertEquals(null, repo.lastSeed)
    }
}
