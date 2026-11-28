package com.zurus.cinethread.data.pairsource

import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.data.bank.PairBankLoader
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.repository.PairSource
import com.zurus.cinethread.domain.util.PairGenConfig
import javax.inject.Inject

/**
 * D8 (docs/debugging.md): дефолтная стратегия (режим `auto`).
 *
 * Запрошенная глубина покрыта банком — пара из банка (0 сетевых
 * запросов, D4/D7); не покрыта — онлайн, как раньше (BFS). Без ошибок,
 * без UI-переключателя: экран игры не знает, откуда пара.
 *
 * Эквивалентность: `auto` ≡ D7-флаг `pairBank.enabled = true`,
 * онлайн-ветка `auto` ≡ режим `online` (D8 заменяет D7-флаг).
 */
class AutoPairSource @Inject constructor(
    private val pairBankLoader: PairBankLoader,
    private val bank: BankPairSource,
    private val online: OnlinePairSource,
) : PairSource {

    override suspend fun pick(
        config: PairGenConfig,
        seed: Long?,
    ): Result<GamePair, PairGenerationError> {
        val depth = config.maxSteps
        return if (pairBankLoader.load().coversDepth(depth)) {
            bank.pick(config, seed)
        } else {
            online.pick(config, seed)
        }
    }
}
