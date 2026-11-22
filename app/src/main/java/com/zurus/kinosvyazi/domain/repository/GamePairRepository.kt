package com.zurus.kinosvyazi.domain.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.GamePair

/**
 * Ошибка генерации пары (docs/04_algorithms.md §3.2, Этап 3).
 *
 * [BudgetExceeded] и [NoValidPair] — валидные игровые состояния,
 * а не сбои (docs/04_algorithms.md §4.5): UI предлагает «попробовать
 * ещё раз», а не экран ошибки.
 */
sealed interface PairGenerationError {

    /** Бюджет генерации исчерпан (запросы/узлы/время). */
    data class BudgetExceeded(val reason: String) : PairGenerationError

    /** Подходящей пары не найдено: пустой пул или нет целей в пределах `maxSteps`. */
    data object NoValidPair : PairGenerationError

    /** Ошибка данных (сеть, сервер, Room). */
    data class DataError(val error: AppError) : PairGenerationError
}

/**
 * Генерация гарантированно решаемых пар (docs/04_algorithms.md §2–§3).
 *
 * Генерация выполняется в пределах бюджета и завершается
 * [PairGenerationError.BudgetExceeded] при исчерпании (§6).
 */
interface GamePairRepository {

    /**
     * Генерирует пару с гарантированным решением.
     *
     * @param seed сид воспроизводимой генерации или `null` — случайный.
     * @return пара с гарантированной кратчайшей цепочкой
     * либо [PairGenerationError].
     */
    suspend fun generatePair(seed: Long?): Result<GamePair, PairGenerationError>
}

/**
 * Преобразование в общий [AppError] для UI-сообщений
 * (docs/01_concept.md §2.3: сообщение об ошибке и «Повторить»).
 */
fun PairGenerationError.toAppError(): AppError = when (this) {
    is PairGenerationError.BudgetExceeded -> AppError.BudgetExceeded(reason)
    PairGenerationError.NoValidPair -> AppError.DataInvalid("Подходящей пары не найдено")
    is PairGenerationError.DataError -> error
}
