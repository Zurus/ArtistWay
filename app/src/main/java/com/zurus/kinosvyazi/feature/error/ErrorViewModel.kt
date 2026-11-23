package com.zurus.kinosvyazi.feature.error

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.core.ui.UiText
import com.zurus.kinosvyazi.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Экран ошибки (docs/01_concept.md §2.10):
 * различает «нет сети», «TMDB недоступен», 429, пустой/некорректный ответ
 * и сбой генерации; предлагает «Повторить» (если повтор возможен) и «В меню».
 *
 * Тип ошибки приходит из аргумента маршрута `error/{type}`
 * ([SavedStateHandle] — переживает ротацию и process death).
 * Навигация («Повторить» → возврат к источнику ошибки, «В меню» → HOME)
 * выполняется экраном.
 */
@HiltViewModel
class ErrorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val uiState: StateFlow<UiState<ErrorUiState>> = MutableStateFlow(
        UiState.Content(buildState(savedStateHandle)),
    ).asStateFlow()

    private fun buildState(savedStateHandle: SavedStateHandle): ErrorUiState {
        val type = ErrorType.fromRoute(savedStateHandle.get<String>(NavRoutes.ERROR_TYPE))
        return ErrorUiState(
            type = type,
            title = UiText.Resource(type.titleId),
            text = UiText.Resource(type.textId),
        )
    }
}
