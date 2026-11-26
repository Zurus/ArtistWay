package com.zurus.cinethread.feature.chain

import com.zurus.cinethread.core.ui.ChainMaps
import com.zurus.cinethread.domain.model.GameSession

/** Содержимое экрана просмотра цепочки (docs/01_concept.md, §2.11). */
data class ChainUiState(
    val session: GameSession,
    val chain: ChainMaps,
)
