package com.zurus.cinethread.data.pairsource

import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.repository.GamePairRepository
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.repository.PairSource
import com.zurus.cinethread.domain.util.PairGenConfig
import javax.inject.Inject

/**
 * D8 (docs/debugging.md): онлайн-источник — адаптер над
 * [GamePairRepository] (логика [GamePairRepositoryImpl]: бюджеты D1,
 * d=2-оптимизация D5, BFS, toDomain-гидрация актёров).
 *
 * Поведение идентично «как сейчас» (режим `online`).
 */
class OnlinePairSource @Inject constructor(
    private val gamePairRepository: GamePairRepository,
) : PairSource {

    override suspend fun pick(
        config: PairGenConfig,
        seed: Long?,
    ): Result<GamePair, PairGenerationError> = gamePairRepository.generatePair(seed, config.maxSteps)
}
