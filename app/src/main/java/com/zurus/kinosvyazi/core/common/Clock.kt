package com.zurus.kinosvyazi.core.common

/**
 * Источник времени для доменных операций (завершение партии и т.п.).
 *
 * Пространство `core.common` — общий для всех слоёв, поэтому домен
 * может зависеть от [Clock], не нарушая правило «в domain нет
 * Android/Room/Retrofit» (docs/05_roadmap.md, Этап 3).
 *
 * В продакшене — системные часы (`di/AppModule`); в unit-тестах —
 * фиксированное значение для детерминизма.
 */
fun interface Clock {

    /** Текущее время, мс с эпохи. */
    fun nowMillis(): Long
}
