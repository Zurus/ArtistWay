package com.zurus.kinosvyazi.feature.actorpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.IoDispatcher
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.core.ui.ErrorMapper
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.usecase.GetMovieCastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран выбора актёра (docs/01_concept.md, §2.6):
 * каст выбранного фильма (UseCase [GetMovieCastUseCase]) без текущего актёра;
 * целевой актёр помечен «Цель».
 */
@HiltViewModel
class ActorPickerViewModel @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val movieRepository: MovieRepository,
    private val getMovieCastUseCase: GetMovieCastUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ActorPickerUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<ActorPickerUiState>> = _uiState.asStateFlow()

    fun load(sessionId: String, movieId: Int) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val session = withContext(ioDispatcher) { gameSessionRepository.findSession(sessionId) }
            if (session == null) {
                _uiState.value = UiState.Error(ErrorMapper.map(AppError.DataInvalid("Партия не найдена")))
                return@launch
            }
            val movie = withContext(ioDispatcher) {
                when (val result = movieRepository.getMovie(movieId)) {
                    is Result.Success -> result.value
                    is Result.Failure -> null
                }
            }
            when (val castResult = withContext(ioDispatcher) { getMovieCastUseCase(movieId) }) {
                is Result.Success -> {
                    val filtered = castResult.value.filter { it.tmdbId != session.currentActor.tmdbId }
                    if (filtered.isEmpty()) {
                        _uiState.value = UiState.Empty
                    } else {
                        _uiState.value = UiState.Content(
                            ActorPickerUiState(
                                currentActor = session.currentActor,
                                targetActor = session.targetActor,
                                movie = movie,
                                actors = filtered,
                            ),
                        )
                    }
                }
                is Result.Failure ->
                    _uiState.value = UiState.Error(ErrorMapper.map(castResult.error))
            }
        }
    }
}
