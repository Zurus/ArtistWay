package com.zurus.kinosvyazi.feature.home

/**
 * Содержимое экрана запуска (docs/01_concept.md, §2.1).
 *
 * @property tmdbAvailable отвечает ли TMDB (индикатор доступности данных).
 */
data class HomeUiState(
    val tmdbAvailable: Boolean,
)
