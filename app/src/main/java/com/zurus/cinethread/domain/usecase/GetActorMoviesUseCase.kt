package com.zurus.cinethread.domain.usecase

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.Movie
import com.zurus.cinethread.domain.repository.ActorRepository
import javax.inject.Inject

/**
 * Фильмография актёра для экрана выбора фильма (docs/01_concept.md §2.5).
 *
 * Тонкая обёртка над [ActorRepository]: UI обращается к домену
 * только через UseCase (docs/02_architecture.md).
 */
class GetActorMoviesUseCase @Inject constructor(
    private val actorRepository: ActorRepository,
) {

    /** @param actorId TMDB ID актёра. @return фильмы или сетевая/кэш-ошибка. */
    suspend operator fun invoke(actorId: Int): Result<List<Movie>, AppError> =
        actorRepository.getActorMovies(actorId)
}
