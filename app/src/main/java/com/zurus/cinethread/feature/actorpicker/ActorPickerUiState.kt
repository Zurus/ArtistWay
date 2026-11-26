package com.zurus.cinethread.feature.actorpicker

import com.zurus.cinethread.domain.model.Actor
import com.zurus.cinethread.domain.model.Movie

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
