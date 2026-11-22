package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.Movie
import com.zurus.kinosvyazi.domain.repository.ActorRepository
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
