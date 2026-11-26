package com.zurus.cinethread.data.mapper

import com.zurus.cinethread.data.local.entity.ActorMovieCreditEntity
import com.zurus.cinethread.data.local.entity.MovieEntity
import com.zurus.cinethread.data.remote.dto.TmdbPersonMovieCreditDto
import javax.inject.Inject

/**
 * Маппинг `TmdbPersonMovieCreditDto` → `MovieEntity` + `ActorMovieCreditEntity`
 * (docs/03_data_layer.md §4): запись с `id = null` не сохраняется.
 */
interface PersonMovieCreditMapper {

    fun toMovieEntity(dto: TmdbPersonMovieCreditDto, fetchedAtMillis: Long): MovieEntity?

    fun toCreditEntity(dto: TmdbPersonMovieCreditDto, personId: Int, fetchedAtMillis: Long): ActorMovieCreditEntity?

    class Default @Inject constructor() : PersonMovieCreditMapper {

        override fun toMovieEntity(dto: TmdbPersonMovieCreditDto, fetchedAtMillis: Long): MovieEntity? =
            dto.id?.let { movieId ->
                MovieEntity(
                    movieId = movieId,
                    title = safeTitle(dto.title, dto.originalTitle),
                    originalTitle = dto.originalTitle,
                    overview = null,
                    releaseDate = dto.releaseDate,
                    posterPath = dto.posterPath,
                    backdropPath = null,
                    runtime = null,
                    voteAverage = null,
                    voteCount = null,
                    adult = dto.adult,
                    fetchedAtMillis = fetchedAtMillis,
                )
            }

        override fun toCreditEntity(
            dto: TmdbPersonMovieCreditDto,
            personId: Int,
            fetchedAtMillis: Long,
        ): ActorMovieCreditEntity? =
            dto.id?.let { movieId ->
                ActorMovieCreditEntity(
                    personId = personId,
                    movieId = movieId,
                    character = dto.character,
                    creditId = dto.creditId,
                    fetchedAtMillis = fetchedAtMillis,
                )
            }

        private fun safeTitle(title: String?, originalTitle: String?): String =
            title?.takeIf { it.isNotBlank() }
                ?: originalTitle?.takeIf { it.isNotBlank() }
                ?: UNTITLED

        private companion object {
            const val UNTITLED = "Без названия"
        }
    }
}
