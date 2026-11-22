package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import javax.inject.Inject

/**
 * Восстановление активной партии после перезапуска приложения
 * (docs/03_data_layer.md §8, docs/01_concept.md §6.12).
 */
class GetGameSessionUseCase @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
) {

    /** @return активная (`IN_PROGRESS`) партия либо `null`, если её нет. */
    suspend operator fun invoke(): GameSession? =
        gameSessionRepository.restoreActiveSession()
}
