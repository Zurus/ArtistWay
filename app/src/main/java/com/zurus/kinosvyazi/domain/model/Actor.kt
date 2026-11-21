package com.zurus.kinosvyazi.domain.model

/**
 * Актёр в игре.
 *
 * @property tmdbId уникальный идентификатор актёра в TMDB.
 * @property name имя актёра (ответ TMDB для `language=en-US`, docs/03_data_layer.md §3).
 * @property photoUrl полный URL фото или `null`, если фото нет.
 */
data class Actor(
    val tmdbId: Int,
    val name: String,
    val photoUrl: String?,
)
