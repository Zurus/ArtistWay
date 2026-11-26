package com.zurus.cinethread.feature.game

import com.zurus.cinethread.core.ui.ChainMaps
import com.zurus.cinethread.domain.model.GameSession
import com.zurus.cinethread.domain.usecase.DeadEndStatus

/**
 * Содержимое экрана активной партии (docs/01_concept.md, §2.4):
 * [session] + резолvedанные цепочки [chain] для отображения.
 *
 * @property deadEndStatus результат последней проверки тупика
 *   ([CheckDeadEndUseCase], docs/04_algorithms.md §4): `null`, если проверка
 *   не выполнялась (пустая цепочка / завершённая партия), `DeadEnd` —
 *   показать диалог «Отменить шаг / Сдаться / Новая партия»,
 *   `Ok(1)` — подсказка «Цель достижима одним переходом».
 *   `Unknown` (сбой/бюджет) игроку не показывается — тупик не наказуем.
 */
data class GameUiState(
    val session: GameSession,
    val chain: ChainMaps,
    val deadEndStatus: DeadEndStatus? = null,
)
