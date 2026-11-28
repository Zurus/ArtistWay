package com.zurus.cinethread.data.pairsource

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.repository.PairSource
import com.zurus.cinethread.domain.util.PairGenConfig
import javax.inject.Inject

/**
 * D8 (docs/debugging.md): stub удалённого микросервиса (P3).
 *
 * Отдаёт **понятную** [PairGenerationError] (UI покажет обычный
 * «Не удалось найти пару»), не падает с NetworkUnavailable/Timeout —
 * микросервис не существует; слот под будущую реализацию, контракт
 * `pick` зафиксирован.
 */
class RemotePairSource @Inject constructor() : PairSource {

    override suspend fun pick(
        config: PairGenConfig,
        seed: Long?,
    ): Result<GamePair, PairGenerationError> =
        Result.failure(
            PairGenerationError.DataError(
                AppError.DataInvalid("pair microservice is not available yet (P3)"),
            ),
        )
}
