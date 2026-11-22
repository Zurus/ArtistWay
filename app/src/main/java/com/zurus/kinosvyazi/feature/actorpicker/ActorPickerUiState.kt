package com.zurus.kinosvyazi.feature.actorpicker

import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.Movie

/**
 * Содержимое экрана выбора актёра (docs/01_concept.md, §2.6):
 * текущий актёр, цель, фильм и актёры его каста (без текущего актёра).
 */
data class ActorPickerUiState(
    val currentActor: Actor,
    val targetActor: Actor,
    val movie: Movie?,
    val actors: List<Actor>,
)
