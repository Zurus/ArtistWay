package com.zurus.cinethread.feature.home

/**
 * Содержимое экрана запуска (docs/01_concept.md, §2.1).
 *
 * @property tmdbAvailable отвечает ли TMDB (индикатор доступности данных).
 * @property activeSessionId незавершённая партия в Room
 *   (docs/01_concept.md §6.17 — при перезапуске приложения игра
 *   восстанавливается, кнопка «Продолжить игру» ведёт на экран партии).
 */
data class HomeUiState(
    val tmdbAvailable: Boolean,
    val activeSessionId: String? = null,
)
