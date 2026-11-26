package com.zurus.cinethread.data.time

/**
 * Источник времени для data layer (кэширование, TTL, метки времени партий).
 *
 * В продакшене — системные часы (см. `di/AppModule`); в unit-тестах
 * репозиториев подставляется управляемый `Clock` для детерминированных
 * сценариев TTL (docs/05_roadmap.md, Этап 2).
 */
fun interface Clock {

    /** Текущее время, мс с эпохи. */
    fun nowMillis(): Long
}
