package com.zurus.cinethread.data.bank

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D7 (docs/debugging.md): загрузчик банка пар — корректный файл,
 * отсутствует, битый, флаг выкл, разбор один раз.
 */
class PairBankLoaderTest {

    private val validBank = """
        {
          "version": 1,
          "pool": [31, 38, 39],
          "names": {"31": "Tom Hanks", "38": "Harrison Ford", "39": "Robin Williams"},
          "pairs": [
            {"s": 31, "t": 38, "d": 2, "p": [101, 900, 102]},
            {"s": 38, "t": 31, "d": 2, "p": [103, 901, 104]},
            {"s": 39, "t": 31, "d": 3, "p": [111, 910, 112, 911, 113]},
            {"s": 31, "t": 31, "d": 2, "p": [101, 900, 102]},
            {"s": 38, "t": 39, "d": 5, "p": [1, 2, 3, 4, 5, 6, 7, 8, 9]},
            {"s": 39, "t": 38, "d": 2, "p": [105, 902]}
          ]
        }
    """.trimIndent()

    private fun readerOf(json: String): AssetReader {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        return AssetReader { _: String -> ByteArrayInputStream(bytes) }
    }

    @Test
    fun `valid file is parsed into depth index with names`() {
        val bank = PairBankLoader(enabled = true, readerOf(validBank)).load()

        assertEquals(1, bank.version)
        assertEquals("Tom Hanks", bank.names[31])
        assertEquals("Harrison Ford", bank.names[38])
        assertEquals(2, bank.pairsByDepth[2]?.size, "валидных d=2 пары: 2")
        assertEquals(1, bank.pairsByDepth[3]?.size, "одна d=3 пара")
        assertFalse(bank.isEmpty)
        assertTrue(bank.coversDepth(2))
        assertTrue(bank.coversDepth(3))
        assertFalse(bank.coversDepth(4), "d=4 в банке нет")
    }

    @Test
    fun `invalid entries are skipped`() {
        val bank = PairBankLoader(enabled = true, readerOf(validBank)).load()

        // s == t, d вне 2..4, len(p) != 2d-1 — все три отброшены.
        val d2 = bank.pairsByDepth[2].orEmpty()
        val ids = d2.map { it.startId to it.targetId }
        assertEquals(listOf(31 to 38, 38 to 31), ids)
        d2.forEach { pair ->
            assertEquals(3, pair.path.size)
        }
    }

    @Test
    fun `random is deterministic by rng seed and covers depth only`() {
        val bank = PairBankLoader(enabled = true, readerOf(validBank)).load()

        val first = bank.random(2, Random(42L))
        val second = bank.random(2, Random(42L))
        assertEquals(first, second, "один seed -> одна пара банка")
        first?.let {
            assertTrue(it.depth == 2)
            assertTrue(setOf(31, 38).containsAll(listOf(it.startId, it.targetId)))
        }
        assertNull(bank.random(4, Random(1L)), "непокрытая глубина -> null")
    }

    @Test
    fun `missing asset falls back to empty bank`() {
        val bank = PairBankLoader(enabled = true) {
            throw FileNotFoundException("pairs_bank_v1.json")
        }.load()

        assertEquals(PairBank.EMPTY, bank)
        assertFalse(bank.coversDepth(2))
    }

    @Test
    fun `corrupt asset falls back to empty bank`() {
        val bank = PairBankLoader(
            enabled = true,
            AssetReader {
                ByteArrayInputStream("это не json {".toByteArray(StandardCharsets.UTF_8))
            },
        ).load()

        assertEquals(PairBank.EMPTY, bank)
    }

    @Test
    fun `disabled flag returns empty bank without touching assets`() {
        var opened = 0
        val bank = PairBankLoader(enabled = false, AssetReader {
            opened++
            throw AssertionError("reader не должен вызываться")
        }).load()

        assertEquals(0, opened, "флаг выкл — ассет не открывается")
        assertEquals(PairBank.EMPTY, bank)
    }

    @Test
    fun `asset is parsed only once`() {
        var opened = 0
        val loader = PairBankLoader(enabled = true, AssetReader {
            opened++
            ByteArrayInputStream(validBank.toByteArray(StandardCharsets.UTF_8))
        })

        val first = loader.load()
        val second = loader.load()

        assertEquals(1, opened, "ассет открывается один раз")
        assertEquals(first, second)
        assertTrue(second.coversDepth(2))
    }
}
