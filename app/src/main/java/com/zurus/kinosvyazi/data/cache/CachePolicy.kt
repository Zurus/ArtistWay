package com.zurus.kinosvyazi.data.cache

import java.time.Duration

/**
 * Политики кэширования (docs/03_data_layer.md §5).
 * TTL хранится в поле `fetchedAtMillis` записей Room;
 * запись устаревает, когда `now - fetchedAtMillis > TTL`.
 */
object CachePolicy {

    val ACTOR_TTL: Duration = Duration.ofDays(30)

    val MOVIE_TTL: Duration = Duration.ofDays(30)

    val ACTOR_MOVIE_CREDITS_TTL: Duration = Duration.ofDays(7)

    val MOVIE_CAST_TTL: Duration = Duration.ofDays(7)

    /** Конфигурация изображений TMDB: 24 часа в памяти. */
    val TMDB_CONFIGURATION_TTL: Duration = Duration.ofHours(24)

    /** Краткосрочный in-memory кэш списка в репозитории. */
    val IN_MEMORY_LIST_TTL: Duration = Duration.ofMinutes(5)

    fun isFresh(fetchedAtMillis: Long, ttl: Duration, nowMillis: Long): Boolean =
        fetchedAtMillis > 0 && nowMillis - fetchedAtMillis <= ttl.toMillis()
}
