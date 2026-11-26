package com.zurus.cinethread.core.ui

import com.zurus.cinethread.R
import com.zurus.cinethread.core.common.AppError

/**
 * Единое место отображаемых текстов:
 * либо литерал, либо ссылка на строку из `res/values/strings.xml`.
 */
sealed interface UiText {

    /** Прямой текст (например, из пользовательского ввода). */
    data class Literal(val value: String) : UiText

    /** Строковый ресурс.
     * @param id идентификатор ресурса (`R.string.*`).
     */
    data class Resource(val id: Int) : UiText
}

/**
 * Маппинг доменных ошибок на пользовательские сообщения
 * (docs/01_concept.md, §7.12: кратко, по-русски, без технических деталей).
 */
object ErrorMapper {

    fun map(error: AppError): UiText =
        when (error) {
            AppError.Network -> UiText.Resource(R.string.error_network)
            AppError.Server -> UiText.Resource(R.string.error_server)
            is AppError.RateLimit -> UiText.Resource(R.string.error_rate_limit)
            is AppError.DataInvalid -> UiText.Resource(R.string.error_data_invalid)
            AppError.Offline -> UiText.Resource(R.string.error_offline)
            is AppError.BudgetExceeded -> UiText.Resource(R.string.error_budget_exceeded)
        }
}
