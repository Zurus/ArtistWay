package com.zurus.cinethread.domain.usecase

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.GameResultReason
import com.zurus.cinethread.domain.model.GameSession
import com.zurus.cinethread.domain.repository.GameSessionRepository
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.repository.PairSource
import com.zurus.cinethread.domain.util.PairGenConfig
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
 *
 * D8 (docs/debugging.md): пара берётся у [PairSource] (стратегия —
 * онлайн / банк / auto / remote, выбор в DI по `BuildConfig.PAIR_SOURCE`).
 * Use case не знает источник: передаёт конфиг точной глубины
 * (`PairGenConfig.forDepth(depth)`) и nullable-сид (D3).
 */
class StartGameUseCase @Inject constructor(
    private val pairSource: PairSource,
    private val pairGenConfig: PairGenConfig,
    private val gameSessionRepository: GameSessionRepository,
    private val finishGameUseCase: FinishGameUseCase,
) {

    /**
     * @param seed сид воспроизводимой генерации или `null` — случайный.
     * @param depth глубина пары, 2..4 (D5): 2 — банк/дешёвый поиск,
     * 3–4 — онлайн-BFS (в режиме `auto` — по доступности в банке).
     */
    suspend operator fun invoke(
        seed: Long? = null,
        depth: Int = 2,
    ): Result<GameSession, PairGenerationError> {
        if (depth !in SUPPORTED_DEPTHS) {
            return Result.failure(
                PairGenerationError.DataError(
                    AppError.DataInvalid("Глубина пары $depth: поддерживается ${SUPPORTED_DEPTHS}"),
                ),
            )
        }
        return when (val pair = pairSource.pick(pairGenConfig.forDepth(depth), seed)) {
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

    private companion object {
        /** D5: поддерживаемые глубины пары (выбор на стартовом экране). */
        val SUPPORTED_DEPTHS = 2..4
    }
}
