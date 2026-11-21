package com.zurus.kinosvyazi.domain.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.model.GameResult
import com.zurus.kinosvyazi.domain.model.GameSession

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
}
