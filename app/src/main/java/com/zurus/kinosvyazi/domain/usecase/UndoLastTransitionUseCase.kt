package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import javax.inject.Inject

/**
 * Отмена последнего перехода (Undo, docs/04_algorithms.md §4.4,
 * docs/01_concept.md §6.16).
 *
 * Пустая цепочка — сессия возвращается как есть, без обращения к
 * хранилищу и без ошибки (документированное поведение §4.4).
 * Сетевых запросов не выполняет: переходы не удаляются из кэша.
 */
class UndoLastTransitionUseCase @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
) {

    suspend operator fun invoke(session: GameSession): Result<GameSession, AppError> {
        if (session.chain.isEmpty()) {
            return Result.success(session)
        }
        return gameSessionRepository.undoLastStep(session)
    }
}
