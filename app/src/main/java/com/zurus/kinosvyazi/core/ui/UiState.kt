package com.zurus.kinosvyazi.core.ui

/**
 * Состояние экрана для MVVM (docs/02_architecture.md, §4).
 *
 * @param Content тип содержимого при успехе.
 */
sealed interface UiState<out Content> {

    /** Идёт загрузка данных. */
    data object Loading : UiState<Nothing>

    /** Данные загружены. */
    data class Content<out T>(val value: T) : UiState<T>

    /** Данные отсутствуют (пустой список, нет сохранённой партии). */
    data object Empty : UiState<Nothing>

    /** Ошибка, отображаемая через [text]. */
    data class Error(val text: UiText) : UiState<Nothing>
}
