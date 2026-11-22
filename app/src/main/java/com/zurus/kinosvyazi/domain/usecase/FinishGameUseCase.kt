package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Clock
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.GameResult
import com.zurus.kinosvyazi.domain.model.GameResultReason
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import javax.inject.Inject

/**
 * Завершение партии (docs/01_concept.md §6.19, docs/05_roadmap.md, Этап 3).
 *
 * Формирует [GameResult] с причиной ([GameResultReason]) и полной
 * цепочкой, сохраняет партию в Room и возвращает итог для экранов
 * результата (§2.8–2.11).
 */
class FinishGameUseCase @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val clock: Clock,
) {

    /**
     * @param session завершаемая партия (активная).
     * @param reason причина: `WIN`, `SURRENDER`, `DEAD_END`, `ERROR`,
     * `NEW_GAME` (игрок начал новую партию, бросив старую).
     * @return итог партии с полной цепочкой.
     */
    suspend operator fun invoke(
        session: GameSession,
        reason: GameResultReason,
    ): Result<GameResult, AppError> {
        val result = GameResult(
            reason = reason,
            startActor = session.startActor,
            targetActor = session.targetActor,
            lastActor = session.currentActor,
            chain = session.chain,
            stepCount = session.stepCount,
            guaranteedDistance = session.shortestPath?.size,
            startedAtMillis = session.startedAtMillis,
            finishedAtMillis = clock.nowMillis(),
        )
        return gameSessionRepository.finishSession(session, result).map { result }
    }
}
