package com.zurus.cinethread.di

import com.zurus.cinethread.data.bank.PairBankLoader
import com.zurus.cinethread.data.pairsource.AutoPairSource
import com.zurus.cinethread.data.pairsource.BankPairSource
import com.zurus.cinethread.data.pairsource.OnlinePairSource
import com.zurus.cinethread.data.pairsource.RemotePairSource
import com.zurus.cinethread.domain.FakeGamePairRepository
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * D8 (docs/debugging.md): выбор [com.zurus.cinethread.domain.repository.PairSource]
 * по `PAIR_SOURCE` — 4 значения + unknown → auto (критерий приёмки D8).
 */
class PairSourceModuleTest {

    private val loader = PairBankLoader(true) { ByteArrayInputStream("{}".toByteArray()) }
    private val bank = BankPairSource(loader)
    private val online = OnlinePairSource(FakeGamePairRepository())

    @Test
    fun `online selects OnlinePairSource`() {
        assertSame(online, PairSourceModule.selectPairSource("online", bank, online, loader))
    }

    @Test
    fun `bank selects BankPairSource`() {
        assertSame(bank, PairSourceModule.selectPairSource("bank", bank, online, loader))
    }

    @Test
    fun `remote selects RemotePairSource`() {
        assertIs<RemotePairSource>(PairSourceModule.selectPairSource("remote", bank, online, loader))
    }

    @Test
    fun `auto selects AutoPairSource`() {
        assertIs<AutoPairSource>(PairSourceModule.selectPairSource("auto", bank, online, loader))
    }

    @Test
    fun `unknown value falls back to AutoPairSource`() {
        assertIs<AutoPairSource>(PairSourceModule.selectPairSource("definitely-not-a-value", bank, online, loader))
    }

    @Test
    fun `value is case- and space-insensitive`() {
        assertIs<AutoPairSource>(PairSourceModule.selectPairSource("  AUTO  ", bank, online, loader))
        assertSame(online, PairSourceModule.selectPairSource("Online", bank, online, loader))
    }
}
