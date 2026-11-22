package com.zurus.kinosvyazi.feature.game

import com.zurus.kinosvyazi.domain.model.GameSession

/**
 * Содержимое экрана генерации партии (docs/01_concept.md, §2.3):
 * созданная [GameSession] — навигация на экран активной партии.
 */
data class GameSetupUiState(
    val session: GameSession,
)
