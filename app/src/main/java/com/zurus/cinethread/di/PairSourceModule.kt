package com.zurus.cinethread.di

import android.util.Log
import com.zurus.cinethread.BuildConfig
import com.zurus.cinethread.data.bank.PairBankLoader
import com.zurus.cinethread.data.pairsource.AutoPairSource
import com.zurus.cinethread.data.pairsource.BankPairSource
import com.zurus.cinethread.data.pairsource.OnlinePairSource
import com.zurus.cinethread.data.pairsource.RemotePairSource
import com.zurus.cinethread.domain.repository.PairSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * D8 (docs/debugging.md): выбор источника пары (стратегия [PairSource])
 * по `BuildConfig.PAIR_SOURCE` — строка из `local.properties`
 * (`pairSource`, дефолт `auto`):
 *
 * | значение | стратегия                                              |
 * |----------|--------------------------------------------------------|
 * | `auto`   | банк покрывает глубину → банк (0 запросов), иначе онлайн |
 * | `online` | только онлайн (текущее поведение)                       |
 * | `bank`   | только банк, без фолбэка                                |
 * | `remote` | stub (понятная ошибка, микросервис — P3)                |
 *
 * Неизвестное значение → `auto` + warning в лог.
 * Заменяет D7-флаг `pairBank.enabled` (`auto` ≡ `true`, `online` ≡ `false`).
 */
@Module
@InstallIn(SingletonComponent::class)
object PairSourceModule {

    /** Тестируемое ядро выбора (критерий D8: 4 значения + unknown → auto). */
    internal fun selectPairSource(
        raw: String,
        bank: BankPairSource,
        online: OnlinePairSource,
        pairBankLoader: PairBankLoader,
    ): PairSource = when (raw.trim().lowercase()) {
        "online" -> online
        "bank" -> bank
        "remote" -> RemotePairSource()
        "auto" -> AutoPairSource(pairBankLoader, bank, online)
        else -> {
            Log.w(TAG, "PAIR_SOURCE unknown value \"$raw\" — using auto")
            AutoPairSource(pairBankLoader, bank, online)
        }
    }

    @Provides
    @Singleton
    fun providePairSource(
        bank: BankPairSource,
        online: OnlinePairSource,
        pairBankLoader: PairBankLoader,
    ): PairSource = selectPairSource(BuildConfig.PAIR_SOURCE, bank, online, pairBankLoader)

    private const val TAG = "PairSourceModule"
}
