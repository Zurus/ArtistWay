package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.repository.GamePairRepository
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.PairGenerationError
import com.zurus.kinosvyazi.core.common.Result
import javax.inject.Inject

/**
 * Запуск новой партии (docs/05_roadmap.md, Этап 3).
 *
 * Атомарная операция: генерация гарантированно решаемой пары
 * (docs/04_algorithms.md §3) + создание партии в Room
 * (docs/03_data_layer.md §2).
 *
 * Результат: [GameSession] с [GameSession.shortestPath] длиной
 * ≤ `PairGenConfig.maxSteps` либо типизированная ошибка
 * [PairGenerationError] (`BudgetExceeded`/`NoValidPair` — валидные
 * состояния, а не сбой; §4.5).
 */
class StartGameUseCase @Inject constructor(
    private val gamePairRepository: GamePairRepository,
    private val gameSessionRepository: GameSessionRepository,
) {

    /**
     * @param seed сид воспроизводимой генерации или `null` — случайный.
     */
    suspend operator fun invoke(seed: Long? = null): Result<GameSession, PairGenerationError> {
        return when (val pair = gamePairRepository.generatePair(seed)) {
            is Result.Success -> when (val session = gameSessionRepository.startSession(pair.value)) {
                is Result.Success -> Result.Success(session.value)
                is Result.Failure -> Result.failure(PairGenerationError.DataError(session.error))
            }
            is Result.Failure -> Result.failure(pair.error)
        }
    }
}
