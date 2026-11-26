package com.zurus.cinethread.feature.transition

import com.zurus.cinethread.core.ui.ChainMaps
import com.zurus.cinethread.domain.model.Actor
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.model.GameSession
import com.zurus.cinethread.domain.model.Movie
import com.zurus.cinethread.domain.util.ChainInvalidReason
import com.zurus.cinethread.domain.util.ValidationResult

/**
 * Содержимое экрана подтверждения перехода (docs/01_concept.md, §2.7):
 * превью `A → M → B`, новая цепочка и результат валидации.
 */
data class TransitionUiState(
    val session: GameSession,
    val fromActor: Actor,
    val movie: Movie?,
    val toActor: Actor,
    val candidateStep: ChainStep,
    val chain: ChainMaps,
    val validation: ResultValidation,
)

/** Валидация кандидата для UI: [Valid] или [Invalid] с причиной из [ValidationResult]. */
sealed interface ResultValidation {
    data object Valid : ResultValidation

    data class Invalid(val reason: ChainInvalidReason) : ResultValidation

    companion object {
        fun fromValidation(result: ValidationResult): ResultValidation = when (result) {
            is ValidationResult.Valid -> Valid
            is ValidationResult.Invalid -> Invalid(result.reason)
        }
    }
}
