package com.zurus.kinosvyazi.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.IoDispatcher
import com.zurus.kinosvyazi.core.ui.ChainResolver
import com.zurus.kinosvyazi.core.ui.ErrorMapper
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран результата — не-победа (docs/01_concept.md, §2.9):
 * «Партия завершена» (сдача/тупик), частичная цепочка, метрики,
 * «Новая партия» / «В меню».
 */
@HiltViewModel
class GameResultNonWinViewModel @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val chainResolver: ChainResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<GameResultNonWinUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<GameResultNonWinUiState>> = _uiState.asStateFlow()

    fun load(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val session = withContext(ioDispatcher) { gameSessionRepository.findSession(sessionId) }
            if (session == null) {
                _uiState.value = UiState.Error(ErrorMapper.map(AppError.DataInvalid("Партия не найдена")))
                return@launch
            }
            val maps = withContext(ioDispatcher) { chainResolver.resolve(session) }
            _uiState.value = UiState.Content(GameResultNonWinUiState(session, maps))
        }
    }
}
