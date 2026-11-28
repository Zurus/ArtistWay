package com.zurus.cinethread.data.pairsource

import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.data.bank.BankPair
import com.zurus.cinethread.data.bank.PairBank
import com.zurus.cinethread.data.bank.PairBankLoader
import com.zurus.cinethread.domain.model.Actor
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.repository.PairSource
import com.zurus.cinethread.domain.util.PairGenConfig
import javax.inject.Inject
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

/**
 * D8 (docs/debugging.md): банк-источник — строгий контракт.
 *
 * Пара берётся из оффлайн-банка (D4/D7): 0 сетевых запросов, актёры-концы —
 * из `names` банка (фото — null, как в D7), цепочка — из `p`.
 *
 * **Без фолбэка**: запрошенная глубина не покрыта банком (или банк пуст —
 * ассет отсутствует/бит, флаг `pairSource`/авто-режим решают выше) —
 * [PairGenerationError.NoValidPair].
 */
class BankPairSource @Inject constructor(
    private val pairBankLoader: PairBankLoader,
) : PairSource {

    override suspend fun pick(
        config: PairGenConfig,
        seed: Long?,
    ): Result<GamePair, PairGenerationError> {
        val depth = config.maxSteps
        val bank = pairBankLoader.load()
        if (!bank.coversDepth(depth)) {
            return Result.failure(PairGenerationError.NoValidPair)
        }
        val rng = Random(seed ?: ThreadLocalRandom.current().nextLong())
        val pair = bank.random(depth, rng) ?: return Result.failure(PairGenerationError.NoValidPair)
        return Result.Success(bankToDomain(bank, pair, seed))
    }

    /**
     * BankPair → GamePair (D7): актёры-концы из `names` банка,
     * фото не подгружаем (0 запросов), промежуточные актёры экраны игры
     * разрешают через `ChainResolver`.
     */
    private fun bankToDomain(bank: PairBank, pair: BankPair, seed: Long?): GamePair {
        val names = bank.names
        return GamePair(
            startActor = Actor(pair.startId, names[pair.startId] ?: "Актёр ${pair.startId}", null),
            targetActor = Actor(pair.targetId, names[pair.targetId] ?: "Актёр ${pair.targetId}", null),
            shortestPath = bankPath(pair),
            seed = seed,
        )
    }

    /**
     * `p` = 2d−1 ID: [фА₁, фильм₁, фА₂, …, фильм_{d−1}, фА_d]
     * (D4, docs/pair_sources.md §2). ШАГ i (1-based):
     * `from = i == 1 ? startId : p[2i−3]`, `movie = p[2i−2]`,
     * `to = i == d ? targetId : p[2i−1]`.
     */
    private fun bankPath(pair: BankPair): List<ChainStep> =
        (1..pair.depth).map { step ->
            val from = if (step == 1) pair.startId else pair.path[2 * step - 3]
            val movie = pair.path[2 * step - 2]
            val to = if (step == pair.depth) pair.targetId else pair.path[2 * step - 1]
            ChainStep(step, from, movie, to)
        }
}
