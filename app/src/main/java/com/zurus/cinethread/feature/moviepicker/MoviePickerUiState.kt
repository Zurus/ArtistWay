package com.zurus.cinethread.feature.moviepicker

import com.zurus.cinethread.domain.model.Actor
import com.zurus.cinethread.domain.model.Movie

/**
 * Содержимое экрана выбора фильма (docs/01_concept.md, §2.5):
 * текущий актёр и его фильмография.
 * Пустой список фильмов — отдельное валидное состояние [com.zurus.cinethread.core.ui.UiState.Empty].
 */
data class MoviePickerUiState(
    val currentActor: Actor,
    val movies: List<Movie>,
)
