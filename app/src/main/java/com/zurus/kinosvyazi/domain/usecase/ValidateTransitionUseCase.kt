package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.util.Budget
import com.zurus.kinosvyazi.domain.util.ChainValidator
import com.zurus.kinosvyazi.domain.util.GenCache
import com.zurus.kinosvyazi.domain.util.ValidationResult
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
