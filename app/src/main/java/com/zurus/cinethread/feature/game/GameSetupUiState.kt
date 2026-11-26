package com.zurus.cinethread.feature.game

import com.zurus.cinethread.domain.model.GameSession

/**
 * Содержимое экрана генерации партии (docs/01_concept.md, §2.3):
 * созданная [GameSession] — навигация на экран активной партии.
 */
data class GameSetupUiState(
    val session: GameSession,
)
