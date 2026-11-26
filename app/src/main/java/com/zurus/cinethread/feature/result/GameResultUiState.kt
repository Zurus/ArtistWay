package com.zurus.cinethread.feature.result

import com.zurus.cinethread.core.ui.ChainMaps
import com.zurus.cinethread.domain.model.GameSession

/**
 * Содержимое экрана результата партии (победа),
 * docs/01_concept.md, §2.8: партия + данные цепочки + метрики.
 */
data class GameResultUiState(
    val session: GameSession,
    val chain: ChainMaps,
)
