package com.zurus.cinethread.data.pairsource

import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.data.bank.PairBankLoader
import com.zurus.cinethread.domain.FakeGamePairRepository
import com.zurus.cinethread.domain.actor
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.util.PairGenConfig
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * D8 (docs/debugging.md): AutoPairSource — дефолт (`auto`):
 * глубина покрыта банком → банк (0 сетевых запросов); не покрыта →
 * онлайн, как раньше. Без ошибок и UI-переключателя.
 */
class AutoPairSourceTest {

    private val bankJson = """
        {
          "version": 1,
          "pool": [31, 38],
          "names": {"31": "Tom Hanks", "38": "Harrison Ford"},
          "pairs": [
            {"s": 31, "t": 38, "d": 2, "p": [101, 900, 102]}
          ]
        }
    """.trimIndent()

    private fun loader(json: String = bankJson): PairBankLoader =
        PairBankLoader(true) { ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)) }

    private fun auto(
        bank: PairBankLoader = loader(),
        online: OnlinePairSource = OnlinePairSource(FakeGamePairRepository()),
    ) = AutoPairSource(bank, BankPairSource(bank), online)

    @Test
    fun `depth covered by bank — pair from bank, zero online calls (D4 and D7)`() = runTest {
        val repo = FakeGamePairRepository()
        val source = auto(online = OnlinePairSource(repo))
        val result = source.pick(PairGenConfig().forDepth(2), seed = 42L)
        val pair = (result as Result.Success).value
        assertEquals(31, pair.startActor.tmdbId, "старт — из банка")
        assertEquals(38, pair.targetActor.tmdbId, "цель — из банка")
        assertEquals(
            listOf(
                ChainStep(1, 31, 101, 900),
                ChainStep(2, 900, 102, 38),
            ),
            pair.shortestPath,
            "цепочка — из p банка",
        )
        assertEquals(
            Long.MIN_VALUE,
            repo.lastSeed,
            "онлайн-репозиторий не должен вызываться (0 запросов)",
        )
    }

    @Test
    fun `depth not covered — online, same seed contract`() = runTest {
        val onlinePair = GamePair(
            startActor = actor(1),
            targetActor = actor(3),
            shortestPath = listOf(ChainStep(1, 1, 101, 3)),
            seed = 5L,
        )
        val repo = FakeGamePairRepository(Result.Success(onlinePair))
        val source = auto(online = OnlinePairSource(repo))
        val result = source.pick(PairGenConfig().forDepth(3), seed = 5L)
        val pair = (result as Result.Success).value
        assertEquals(1, pair.startActor.tmdbId, "пара — из онлайн-ветки")
        assertEquals(3, repo.lastDepth, "глубина прошла в онлайн-репозиторий")
        assertEquals(5L, repo.lastSeed)
    }

    @Test
    fun `online failure passes through`() = runTest {
        val repo = FakeGamePairRepository(
            Result.failure(PairGenerationError.NoValidPair),
        )
        val source = auto(online = OnlinePairSource(repo))
        val result = source.pick(PairGenConfig().forDepth(4), seed = 1L)
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.NoValidPair, (result as Result.Failure).error)
    }
}
