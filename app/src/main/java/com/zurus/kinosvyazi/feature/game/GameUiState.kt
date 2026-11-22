package com.zurus.kinosvyazi.feature.game

import com.zurus.kinosvyazi.core.ui.ChainMaps
import com.zurus.kinosvyazi.domain.model.GameSession

/**
 * Содержимое экрана активной партии (docs/01_concept.md, §2.4):
 * [session] + резолvedанные цепочки [chain] для отображения.
 */
data class GameUiState(
    val session: GameSession,
    val chain: ChainMaps,
)
