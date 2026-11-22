package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.util.Budget
import com.zurus.kinosvyazi.domain.util.ChainValidator
import com.zurus.kinosvyazi.domain.util.GenCache
import com.zurus.kinosvyazi.domain.util.ValidationResult
import javax.inject.Inject

/**
 * Итог фиксации перехода (docs/05_roadmap.md, Этап 3).
 *
 * @property session обновлённая партия (шаг зафиксирован в Room).
 * @property isWin `true`, если переход привёл к целевому актёру —
 * UI вызывает [FinishGameUseCase] с причиной `WIN`.
 */
data class CommitTransitionOutcome(
    val session: GameSession,
    val isWin: Boolean,
)

/**
 * Фиксация перехода игрока (docs/04_algorithms.md §2.4, §4).
 *
 * Порядок: валидация кандидата ([ChainValidator] — перед фиксацией
 * перехода), затем сохранение шага в Room. Проверка победы:
 * `currentActor == targetActor` (§4).
 */
class CommitTransitionUseCase @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val actorRepository: ActorRepository,
    private val movieRepository: MovieRepository,
    private val budgetFactory: () -> Budget,
) {

    suspend operator fun invoke(
        session: GameSession,
        movieId: Int,
        toActorId: Int,
    ): Result<CommitTransitionOutcome, AppError> {
        val cache = GenCache(actorRepository, movieRepository, budgetFactory())
        val step = ChainStep(
            stepIndex = session.stepCount + 1,
            fromActorId = session.currentActor.tmdbId,
            movieId = movieId,
            toActorId = toActorId,
        )
        val validation = ChainValidator.validateChain(session.copy(chain = session.chain + step), cache)
        return when (validation) {
            is Result.Failure -> validation
            is Result.Success -> when (val outcome = validation.value) {
                is ValidationResult.Invalid -> Result.failure(AppError.DataInvalid(outcome.message))
                is ValidationResult.Valid -> gameSessionRepository.commitStep(session, step).map { updated ->
                    CommitTransitionOutcome(
                        session = updated,
                        isWin = updated.currentActor.tmdbId == updated.targetActor.tmdbId,
                    )
                }
            }
        }
    }
}
