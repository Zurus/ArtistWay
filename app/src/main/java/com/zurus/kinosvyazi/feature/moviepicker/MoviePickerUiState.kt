package com.zurus.kinosvyazi.feature.moviepicker

import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.Movie

/**
 * Содержимое экрана выбора фильма (docs/01_concept.md, §2.5):
 * текущий актёр и его фильмография.
 * Пустой список фильмов — отдельное валидное состояние [com.zurus.kinosvyazi.core.ui.UiState.Empty].
 */
data class MoviePickerUiState(
    val currentActor: Actor,
    val movies: List<Movie>,
)
