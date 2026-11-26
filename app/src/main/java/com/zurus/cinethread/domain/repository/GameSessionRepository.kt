package com.zurus.cinethread.domain.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.model.GameResult
import com.zurus.cinethread.domain.model.GameSession

/**
 * Хранилище партий (Room, docs/03_data_layer.md §2, §8).
 *
 * Гарантии: сохранение партии и цепочки, восстановление после перезапуска
 * приложения, отмена последнего хода (Undo, docs/01_concept.md §6.16).
 */
interface GameSessionRepository {

    /** Создаёт и сохраняет партию из сгенерированной пары. */
    suspend fun startSession(pair: GamePair): Result<GameSession, AppError>

    /**
     * Коммит следующего шага: добавляет [step] в цепочку
     * и обновляет текущего актёра.
     */
    suspend fun commitStep(session: GameSession, step: ChainStep): Result<GameSession, AppError>

    /** Убирает последний шаг цепочки (Undo). */
    suspend fun undoLastStep(session: GameSession): Result<GameSession, AppError>

    /** Завершает партию, сохраняя [result]. */
    suspend fun finishSession(session: GameSession, result: GameResult): Result<GameSession, AppError>

    /**
     * Возвращает активную партию (статус `IN_PROGRESS`) после перезапуска
     * приложения, либо `null`, если такой нет.
     */
    suspend fun restoreActiveSession(): GameSession?

    /**
     * Возвращает партию по [sessionId] (любого статуса) —
     * для экранов активной партии и результатов (docs/01_concept.md, §2.4, §2.8, §2.9).
     */
    suspend fun findSession(sessionId: String): GameSession?
}
