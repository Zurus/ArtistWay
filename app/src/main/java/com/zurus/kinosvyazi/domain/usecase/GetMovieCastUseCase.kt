package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import javax.inject.Inject

/**
 * Актёрский состав фильма для экрана выбора следующего актёра
 * (docs/01_concept.md §2.6).
 *
 * Тонкая обёртка над [MovieRepository]: UI обращается к домену
 * только через UseCase (docs/02_architecture.md).
 */
class GetMovieCastUseCase @Inject constructor(
    private val movieRepository: MovieRepository,
) {

    /** @param movieId TMDB ID фильма. @return состав (`cast`) или ошибка. */
    suspend operator fun invoke(movieId: Int): Result<List<Actor>, AppError> =
        movieRepository.getMovieCast(movieId)
}
