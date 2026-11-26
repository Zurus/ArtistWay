package com.zurus.cinethread.core.common

/**
 * Типы ошибок приложения.
 *
 * Список зафиксирован (docs/05_roadmap.md, Этап 1, критерий приёмки 6):
 * [Network], [Server], [RateLimit], [DataInvalid], [Offline], [BudgetExceeded].
 *
 * Ошибка не содержит текста для пользователя: отображаемое сообщение
 * выбирает `core.ui.ErrorMapper` по типу (docs/01_concept.md, §7.12).
 */
sealed interface AppError {

    /** Техническое описание для логов (не показывается пользователю). */
    val debugMessage: String

    /** Сбой сетевого соединения: таймаут, DNS, разрыв сокета. */
    data object Network : AppError {
        override val debugMessage: String = "network failure"
    }

    /** Сервис TMDB недоступен (HTTP 5xx). */
    data object Server : AppError {
        override val debugMessage: String = "tmdb server error (5xx)"
    }

    /** Превышен лимит запросов TMDB (HTTP 429). */
    data class RateLimit(val retryAfterSeconds: Int?) : AppError {
        override val debugMessage: String = "tmdb rate limit (429), retryAfterSeconds=$retryAfterSeconds"
    }

    /** Данные TMDB некорректны: сбой парсинга, отсутствие полей, невалидные ID. */
    data class DataInvalid(val details: String?) : AppError {
        override val debugMessage: String = "invalid tmdb data: $details"
    }

    /** Нет соединения с интернетом; операция возможна только из кэша. */
    data object Offline : AppError {
        override val debugMessage: String = "offline"
    }

    /** Бюджет генерации пары (время/запросы) исчерпан, генерация прервана. */
    data class BudgetExceeded(val reason: String) : AppError {
        override val debugMessage: String = "generation budget exceeded: $reason"
    }
}
