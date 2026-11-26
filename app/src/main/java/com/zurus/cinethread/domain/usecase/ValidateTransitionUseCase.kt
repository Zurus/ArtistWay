package com.zurus.cinethread.domain.usecase

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.model.GameSession
import com.zurus.cinethread.domain.repository.ActorRepository
import com.zurus.cinethread.domain.repository.MovieRepository
import com.zurus.cinethread.domain.util.Budget
import com.zurus.cinethread.domain.util.ChainValidator
import com.zurus.cinethread.domain.util.GenCache
import com.zurus.cinethread.domain.util.ValidationResult
import javax.inject.Inject

/**
 * Проверка кандидата на переход `текущий актёр → фильм → следующий актёр`
 * в контексте партии (docs/04_algorithms.md §2.4).
 *
 * Реализовано как валидация цепочки с добавленным шагом — переиспользует
 * все правила [ChainValidator] (разрыв, повторы, cast).
 *
 * @return [ValidationResult.Valid], если переход легален,
 * либо [ValidationResult.Invalid] с причиной.
 */
class ValidateTransitionUseCase @Inject constructor(
    private val actorRepository: ActorRepository,
    private val movieRepository: MovieRepository,
    private val budgetFactory: () -> Budget,
) {

    suspend operator fun invoke(
        session: GameSession,
        movieId: Int,
        toActorId: Int,
    ): Result<ValidationResult, AppError> {
        val cache = GenCache(actorRepository, movieRepository, budgetFactory())
        val candidate = ChainStep(
            stepIndex = session.stepCount + 1,
            fromActorId = session.currentActor.tmdbId,
            movieId = movieId,
            toActorId = toActorId,
        )
        val extended = session.copy(chain = session.chain + candidate)
        return ChainValidator.validateChain(extended, cache)
    }
}
