package com.zurus.cinethread.domain.usecase

import com.zurus.cinethread.domain.model.GameResultReason
import com.zurus.cinethread.domain.model.GameSession
import com.zurus.cinethread.domain.repository.GamePairRepository
import com.zurus.cinethread.domain.repository.GameSessionRepository
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.core.common.Result
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
    private val finishGameUseCase: FinishGameUseCase,
) {

    /**
     * @param seed сид воспроизводимой генерации или `null` — случайный.
     */
    suspend operator fun invoke(seed: Long? = null): Result<GameSession, PairGenerationError> {
        return when (val pair = gamePairRepository.generatePair(seed)) {
            is Result.Success -> {
                // docs/01_concept.md §6.17: предыдущая незавершённая партия
                // закрывается как NEW_GAME, чтобы restoreActiveSession
                // не возвращала мёртвую сессию.
                closeActiveSessionAsNewGame()
                when (val session = gameSessionRepository.startSession(pair.value)) {
                    is Result.Success -> Result.Success(session.value)
                    is Result.Failure ->
                        Result.failure(PairGenerationError.DataError(session.error))
                }
            }
            is Result.Failure -> Result.failure(pair.error)
        }
    }

    private suspend fun closeActiveSessionAsNewGame() {
        val active = gameSessionRepository.restoreActiveSession() ?: return
        finishGameUseCase(active, GameResultReason.NEW_GAME)
    }
}
