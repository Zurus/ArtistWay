package com.zurus.kinosvyazi.data.mapper

import com.zurus.kinosvyazi.data.local.entity.ActorEntity
import com.zurus.kinosvyazi.data.local.entity.MovieCastEntity
import com.zurus.kinosvyazi.data.local.entity.MovieEntity
import com.zurus.kinosvyazi.data.remote.dto.TmdbMovieCastMemberDto
import com.zurus.kinosvyazi.domain.model.MovieCredit
import javax.inject.Inject

/**
 * Маппинг состава фильма (docs/03_data_layer.md §4):
 * `TmdbMovieCastMemberDto` → `MovieCastEntity` (+ строка актёра в `actors`),
 * `MovieCastEntity` + `ActorEntity` + `MovieEntity` → `MovieCredit`.
 *
 * Отклонение от доков: `toDomain` дополнительно принимает фильм
 * и базовый URL изображений — без них `MovieCredit` не собрать.
 */
interface MovieCastMapper {

    /** `null`, если у участника нет `id` — такие записи не сохраняются. */
    fun toEntity(dto: TmdbMovieCastMemberDto, movieId: Int, fetchedAtMillis: Long): MovieCastEntity?

    /** Строка актёра в кэше (имя + фото из `cast`); `null` без `id`. */
    fun toActorEntity(dto: TmdbMovieCastMemberDto, fetchedAtMillis: Long): ActorEntity?

    fun toDomain(
        castEntity: MovieCastEntity,
        actorEntity: ActorEntity,
        movieEntity: MovieEntity,
        imageBaseUrl: String,
    ): MovieCredit

    class Default @Inject constructor() : MovieCastMapper {

        private val movieMapper = MovieMapper.Default()
        private val personMapper = PersonMapper.Default()

        override fun toEntity(
            dto: TmdbMovieCastMemberDto,
            movieId: Int,
            fetchedAtMillis: Long,
        ): MovieCastEntity? =
            dto.id?.let { personId ->
                MovieCastEntity(
                    personId = personId,
                    movieId = movieId,
                    character = dto.character,
                    creditId = dto.creditId,
                    order = dto.order,
                    fetchedAtMillis = fetchedAtMillis,
                )
            }

        override fun toActorEntity(
            dto: TmdbMovieCastMemberDto,
            fetchedAtMillis: Long,
        ): ActorEntity? =
            dto.id?.let { personId ->
                ActorEntity(
                    personId = personId,
                    name = safeName(dto),
                    profilePath = dto.profilePath,
                    knownForDepartment = null,
                    popularity = dto.popularity,
                    fetchedAtMillis = fetchedAtMillis,
                )
            }

        override fun toDomain(
            castEntity: MovieCastEntity,
            actorEntity: ActorEntity,
            movieEntity: MovieEntity,
            imageBaseUrl: String,
        ): MovieCredit =
            MovieCredit(
                movie = movieMapper.toDomain(movieEntity, imageBaseUrl),
                actor = personMapper.toDomain(actorEntity, imageBaseUrl),
                character = castEntity.character,
            )

        private fun safeName(dto: TmdbMovieCastMemberDto): String =
            dto.name?.takeIf { it.isNotBlank() }
                ?: dto.originalName?.takeIf { it.isNotBlank() }
                ?: UNKNOWN_NAME

        private companion object {
            const val UNKNOWN_NAME = "Неизвестный"
        }
    }
}
