package com.zurus.kinosvyazi.data.mapper

import com.zurus.kinosvyazi.data.local.entity.MovieEntity
import com.zurus.kinosvyazi.data.remote.TmdbImageUrls
import com.zurus.kinosvyazi.data.remote.dto.TmdbMovieDto
import com.zurus.kinosvyazi.domain.model.Movie
import javax.inject.Inject
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeParseException
import timber.log.Timber

/**
 * Маппинг `TmdbMovieDto` ↔ `MovieEntity` ↔ `Movie` (docs/03_data_layer.md §4):
 * `id` обязателен, `releaseDate` может отсутствовать, название —
 * `title` → `originalTitle` → заглушка.
 */
interface MovieMapper {

    fun toEntity(dto: TmdbMovieDto, fetchedAtMillis: Long): MovieEntity?

    fun toDomain(entity: MovieEntity, imageBaseUrl: String): Movie

    class Default @Inject constructor() : MovieMapper {

        override fun toEntity(dto: TmdbMovieDto, fetchedAtMillis: Long): MovieEntity? =
            dto.id?.let { movieId ->
                MovieEntity(
                    movieId = movieId,
                    title = safeTitle(dto.title, dto.originalTitle),
                    originalTitle = dto.originalTitle,
                    overview = dto.overview,
                    releaseDate = dto.releaseDate,
                    posterPath = dto.posterPath,
                    backdropPath = dto.backdropPath,
                    runtime = dto.runtime,
                    voteAverage = dto.voteAverage,
                    voteCount = dto.voteCount,
                    adult = dto.adult,
                    fetchedAtMillis = fetchedAtMillis,
                )
            }

        override fun toDomain(entity: MovieEntity, imageBaseUrl: String): Movie =
            Movie(
                tmdbId = entity.movieId,
                title = entity.title.ifBlank { safeTitle(entity.originalTitle, null) },
                year = parseYear(entity.releaseDate),
                posterUrl = TmdbImageUrls.compose(
                    imageBaseUrl,
                    TmdbImageUrls.POSTER_SIZE,
                    entity.posterPath,
                ),
            )

        private fun safeTitle(title: String?, originalTitle: String?): String =
            title?.takeIf { it.isNotBlank() }
                ?: originalTitle?.takeIf { it.isNotBlank() }
                ?: UNTITLED

        private fun parseYear(releaseDate: String?): Year? {
            if (releaseDate.isNullOrBlank()) return null
            return try {
                if (releaseDate.length == 4) Year.parse(releaseDate)
                else Year.of(LocalDate.parse(releaseDate).year)
            } catch (e: DateTimeParseException) {
                Timber.w(e, "Не удалось разобрать releaseDate=%s, год не указывается", releaseDate)
                null
            }
        }

        private companion object {
            const val UNTITLED = "Без названия"
        }
    }
}
