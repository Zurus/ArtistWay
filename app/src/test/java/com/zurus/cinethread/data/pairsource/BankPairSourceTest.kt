package com.zurus.cinethread.data.pairsource

import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.data.bank.PairBankLoader
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.util.PairGenConfig
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * D8 (docs/debugging.md): BankPairSource — строгий контракт:
 * пара только из банка (0 сетевых запросов), глубина не покрыта —
 * [PairGenerationError.NoValidPair], **без фолбэка на онлайн**.
 */
class BankPairSourceTest {

    private val bankJson = """
        {
          "version": 1,
          "pool": [31, 38],
          "names": {"31": "Tom Hanks", "38": "Harrison Ford"},
          "pairs": [
            {"s": 31, "t": 38, "d": 2, "p": [101, 900, 102]},
            {"s": 38, "t": 31, "d": 2, "p": [103, 901, 104]}
          ]
        }
    """.trimIndent()

    private val source = BankPairSource(
        PairBankLoader(true) { ByteArrayInputStream(bankJson.toByteArray(Charsets.UTF_8)) },
    )

    @Test
    fun `covered depth — pair from bank, names from bank, zero network`() = runTest {
        val result = source.pick(PairGenConfig().forDepth(2), seed = 42L)
        val pair = (result as Result.Success).value
        assertEquals(31, pair.startActor.tmdbId, "старт — из банка")
        assertEquals(38, pair.targetActor.tmdbId, "цель — из банка")
        assertEquals("Tom Hanks", pair.startActor.name, "имена — из names банка")
        assertEquals("Harrison Ford", pair.targetActor.name)
        assertNull(pair.startActor.photoUrl, "у банка нет фото")
        assertEquals(
            listOf(
                ChainStep(1, 31, 101, 900),
                ChainStep(2, 900, 102, 38),
            ),
            pair.shortestPath,
            "цепочка из p: A→fA→X→fB→B",
        )
        assertEquals(42L, pair.seed)
    }

    @Test
    fun `deterministic by seed`() = runTest {
        val first = (source.pick(PairGenConfig().forDepth(2), seed = 7L) as Result.Success).value
        val second = (source.pick(PairGenConfig().forDepth(2), seed = 7L) as Result.Success).value
        assertEquals(first, second, "один seed — одна пара банка")
        val other = (source.pick(PairGenConfig().forDepth(2), seed = 8L) as Result.Success).value
        assertNotEquals(first, other, "разные seed — разные пары (2 пары в банке)")
    }

    @Test
    fun `uncovered depth — NoValidPair, no online fallback (strict contract)`() = runTest {
        val result = source.pick(PairGenConfig().forDepth(3), seed = 1L)
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.NoValidPair, (result as Result.Failure).error)
    }

    @Test
    fun `empty bank — NoValidPair even for covered-by-name depth`() = runTest {
        val empty = BankPairSource(PairBankLoader(true) { ByteArrayInputStream("{}".toByteArray()) })
        val result = empty.pick(PairGenConfig().forDepth(2), seed = 1L)
        assertTrue(result is Result.Failure)
        assertEquals(PairGenerationError.NoValidPair, (result as Result.Failure).error)
    }
}
