package com.zurus.cinethread.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.cinethread.core.common.IoDispatcher
import com.zurus.cinethread.core.ui.ChainResolver
import com.zurus.cinethread.core.ui.UiState
import com.zurus.cinethread.domain.repository.GameSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран результата — победа (docs/01_concept.md, §2.8):
 * «Цель достигнута!», полная цепочка, метрики (шаги, время),
 * «Новая партия» / «Посмотреть цепочку» / «В меню».
 */
@HiltViewModel
class GameResultViewModel @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val chainResolver: ChainResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<GameResultUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<GameResultUiState>> = _uiState.asStateFlow()

    /** Загружает партию и резолвит цепочку.
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
            val maps = withContext(ioDispatcher) { chainResolver.resolve(session) }
            _uiState.value = UiState.Content(GameResultUiState(session, maps))
        }
    }
}
