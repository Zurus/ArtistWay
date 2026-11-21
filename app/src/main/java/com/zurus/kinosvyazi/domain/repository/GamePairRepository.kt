package com.zurus.kinosvyazi.domain.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.GamePair

/**
 * Генерация гарантированно решаемых пар (docs/04_algorithms.md §2–§3).
 *
 * Генерация выполняется в пределах бюджета (время/запросы) и прерывается
 * с ошибкой [AppError.BudgetExceeded] при исчерпании (docs/04_algorithms.md §6).
 */
interface GamePairRepository {

    /**
     * Генерирует пару с гарантированным решением.
     *
     * @param seed сид воспроизводимой генерации или `null` — случайный.
     * @return пара с гарантированной кратчайшей цепочкой
     * либо ошибка ([AppError.BudgetExceeded], сетевые ошибки и т.п.).
     */
    suspend fun generatePair(seed: Long?): Result<GamePair, AppError>
}
