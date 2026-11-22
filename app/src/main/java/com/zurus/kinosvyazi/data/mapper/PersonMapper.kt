package com.zurus.kinosvyazi.data.mapper

import com.zurus.kinosvyazi.data.local.entity.ActorEntity
import com.zurus.kinosvyazi.data.remote.TmdbImageUrls
import com.zurus.kinosvyazi.data.remote.dto.TmdbPersonDto
import com.zurus.kinosvyazi.domain.model.Actor
import javax.inject.Inject

/**
 * Маппинг `TmdbPersonDto` ↔ `ActorEntity` ↔ `Actor` (docs/03_data_layer.md §4):
 * `id` обязателен, отсутствующее имя — безопасная заглушка.
 *
 * Отклонение от доков: `toDomain` принимает базовый URL изображений
 * (для сборки `photoUrl` из `profilePath` + configuration).
 */
interface PersonMapper {

    fun toEntity(dto: TmdbPersonDto, fetchedAtMillis: Long): ActorEntity?

    fun toDomain(entity: ActorEntity, imageBaseUrl: String): Actor

    companion object {
        /** Заглушка имени актёра, которого TMDB не вернул (docs/01_concept.md §6.15). */
        const val UNKNOWN_NAME = "Неизвестный"
    }

    class Default @Inject constructor() : PersonMapper {

        override fun toEntity(dto: TmdbPersonDto, fetchedAtMillis: Long): ActorEntity? =
            dto.id?.let { personId ->
                ActorEntity(
                    personId = personId,
                    name = dto.name?.takeIf { it.isNotBlank() } ?: UNKNOWN_NAME,
                    profilePath = dto.profilePath,
                    knownForDepartment = dto.knownForDepartment,
                    popularity = dto.popularity,
                    fetchedAtMillis = fetchedAtMillis,
                )
            }

        override fun toDomain(entity: ActorEntity, imageBaseUrl: String): Actor =
            Actor(
                tmdbId = entity.personId,
                name = entity.name.ifBlank { UNKNOWN_NAME },
                photoUrl = TmdbImageUrls.compose(
                    imageBaseUrl,
                    TmdbImageUrls.PROFILE_SIZE,
                    entity.profilePath,
                ),
            )
    }
}
