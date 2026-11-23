package com.zurus.kinosvyazi.feature.moviepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.kinosvyazi.core.common.IoDispatcher
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.core.ui.ErrorMapper
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.usecase.GetActorMoviesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран выбора фильма (docs/01_concept.md, §2.5):
 * список фильмов с участием текущего актёра (UseCase [GetActorMoviesUseCase]).
 */
@HiltViewModel
class MoviePickerViewModel @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val getActorMoviesUseCase: GetActorMoviesUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<MoviePickerUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<MoviePickerUiState>> = _uiState.asStateFlow()

    /** Загружает фильмы текущего актёра партии.
     *
     * @param onSessionNotFound партия не найдена — экран уходит на ErrorScreen
     *   (docs/01_concept.md §6.17).
     */
    fun load(sessionId: String, onSessionNotFound: (() -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val session = withContext(ioDispatcher) { gameSessionRepository.findSession(sessionId) }
            if (session == null) {
                onSessionNotFound?.invoke()
                return@launch
            }
            when (val result = withContext(ioDispatcher) {
                getActorMoviesUseCase(session.currentActor.tmdbId)
            }) {
                is Result.Success ->
                    if (result.value.isEmpty()) {
                        _uiState.value = UiState.Empty
                    } else {
                        _uiState.value = UiState.Content(
                            MoviePickerUiState(session.currentActor, result.value),
                        )
                    }
                is Result.Failure ->
                    _uiState.value = UiState.Error(ErrorMapper.map(result.error))
            }
        }
    }
}
