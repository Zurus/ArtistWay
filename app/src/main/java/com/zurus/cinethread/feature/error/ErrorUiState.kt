package com.zurus.cinethread.feature.error

import com.zurus.cinethread.R
import com.zurus.cinethread.core.ui.UiText

/**
 * Типы ошибок для экрана ошибки (docs/01_concept.md §2.10, §6.1/6.2/6.3/6.19):
 * нет сети, TMDB недоступен, 429, пустой/некорректный ответ, сбой генерации.
 *
 * @property route значение аргумента маршрута `error/{type}`.
 */
enum class ErrorType(val route: String) {

    /** Партия не найдена в Room (например, после очистки данных). */
    NOT_FOUND("not_found"),

    /** Нет соединения с интернетом (§6.2). */
    NETWORK("network"),

    /** TMDB временно недоступен (§6.2). */
    SERVER("server"),

    /** Превышен лимит запросов, 429 (§6.3). */
    RATE_LIMIT("rate_limit"),

    /** Пустой или некорректный ответ TMDB (§6.19). */
    DATA_INVALID("data_invalid"),

    /** Не удалось подобрать пару (§6.1). */
    GENERATION("generation"),

    /** Неизвестный тип — общий текст. */
    UNKNOWN("unknown");

    val titleId: Int
        get() = when (this) {
            NOT_FOUND -> R.string.error_title_not_found
            NETWORK -> R.string.error_title_network
            SERVER -> R.string.error_title_server
            RATE_LIMIT -> R.string.error_title_rate_limit
            DATA_INVALID -> R.string.error_title_data
            GENERATION -> R.string.error_title_generation
            UNKNOWN -> R.string.error_screen_title
        }

    val textId: Int
        get() = when (this) {
            NOT_FOUND -> R.string.error_text_not_found
            NETWORK -> R.string.error_text_network
            SERVER -> R.string.error_text_server
            RATE_LIMIT -> R.string.error_text_rate_limit
            DATA_INVALID -> R.string.error_text_data
            GENERATION -> R.string.error_text_generation
            UNKNOWN -> R.string.error_text_not_found
        }

    /** Повтор возможен, кроме случая «партия не найдена» (повтор бессмыслен). */
    val canRetry: Boolean
        get() = this != NOT_FOUND

    companion object {
        fun fromRoute(route: String?): ErrorType =
            entries.firstOrNull { it.route == route } ?: UNKNOWN
    }
}

/**
 * Состояние экрана ошибки: заголовок, объяснение без технических деталей,
 * доступность «Повторить».
 */
data class ErrorUiState(
    val type: ErrorType,
    val title: UiText,
    val text: UiText,
) {
    val canRetry: Boolean get() = type.canRetry
}
