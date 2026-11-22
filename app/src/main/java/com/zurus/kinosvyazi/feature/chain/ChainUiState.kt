package com.zurus.kinosvyazi.feature.chain

import com.zurus.kinosvyazi.core.ui.ChainMaps
import com.zurus.kinosvyazi.domain.model.GameSession

/** Содержимое экрана просмотра цепочки (docs/01_concept.md, §2.11). */
data class ChainUiState(
    val session: GameSession,
    val chain: ChainMaps,
)
