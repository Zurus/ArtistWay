package com.zurus.kinosvyazi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.kinosvyazi.core.common.IoDispatcher
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.usecase.CheckDataAvailabilityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран запуска (docs/01_concept.md, §2.1):
 * заголовок, кнопки «Начать игру» / «Как играть», индикатор доступности TMDB.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val checkDataAvailabilityUseCase: CheckDataAvailabilityUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<HomeUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeUiState>> = _uiState.asStateFlow()

    /** Проверяет доступность TMDB (повторно — по кнопке «Повторить подключение»). */
    fun load() {
        if (_uiState.value !is UiState.Loading && _uiState.value is UiState.Content) {
            // Доступность уже проверялась: повторный запуск — только явно (retry).
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = checkAvailability()
        }
    }

    fun retryAvailability() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = checkAvailability()
        }
    }

    private suspend fun checkAvailability(): UiState<HomeUiState> {
        val result: Result<Unit, *> = withContext(ioDispatcher) { checkDataAvailabilityUseCase() }
        return UiState.Content(
            HomeUiState(tmdbAvailable = result is Result.Success),
        )
    }
}
