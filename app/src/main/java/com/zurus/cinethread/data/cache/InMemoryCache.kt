package com.zurus.cinethread.data.cache

/**
 * Краткосрочный in-memory кэш репозитория (docs/02_architecture.md §6,
 * docs/03_data_layer.md §5: «успешный ответ … может быть помещён
 * в краткосрочный in-memory cache»).
 *
 * Простейший TTL-кэш с ограничением размера; не выживает после
 * перезапуска процесса — это осознанно (Room — основной кэш).
 */
class InMemoryCache<V>(
    private val ttlMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxSize: Int = MAX_SIZE,
) {

    private val values = LinkedHashMap<Any, Pair<V, Long>>()
    private val lock = Any()

    fun get(key: Any): V? = synchronized(lock) {
        values[key]?.let { (value, fetchedAt) ->
            if (nowMillis() - fetchedAt <= ttlMillis) {
                value
            } else {
                values.remove(key)
                null
            }
        }
    }

    fun put(key: Any, value: V) = synchronized(lock) {
        values.remove(key)
        values[key] = value to nowMillis()
        while (values.size > maxSize) {
            val oldest = values.keys.first()
            values.remove(oldest)
        }
    }

    fun clear() = synchronized(lock) {
        values.clear()
    }

    private companion object {
        const val MAX_SIZE = 64
    }
}
