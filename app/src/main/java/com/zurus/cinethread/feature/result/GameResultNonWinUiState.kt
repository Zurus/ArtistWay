package com.zurus.cinethread.feature.result

import com.zurus.cinethread.core.ui.ChainMaps
import com.zurus.cinethread.domain.model.GameSession

/**
 * Содержимое экрана результата — не-победа (сдача/тупик),
 * docs/01_concept.md, §2.9: статус, частичная цепочка, метрики.
 */
data class GameResultNonWinUiState(
    val session: GameSession,
    val chain: ChainMaps,
)
