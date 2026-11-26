package com.zurus.cinethread.domain.model

import java.time.Year

/**
 * Фильм в игре.
 *
 * @property tmdbId уникальный идентификатор фильма в TMDB.
 * @property title название (с фолбэком `title → originalTitle`, docs/03_data_layer.md §4).
 * @property year год выхода или `null`, если неизвестен.
 * @property posterUrl полный URL постера или `null`.
 */
data class Movie(
    val tmdbId: Int,
    val title: String,
    val year: Year?,
    val posterUrl: String?,
)
