package com.zurus.cinethread.feature.chain

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
 * Экран просмотра цепочки (docs/01_concept.md, §2.11):
 * полный список шагов партии и метрики (шаги, время).
 */
@HiltViewModel
class ChainViewModel @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val chainResolver: ChainResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ChainUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<ChainUiState>> = _uiState.asStateFlow()

    /** Загружает партию и резолвит цепочку.
     *
     * @param onSessionNotFound партия не найдена — экран уходит на ErrorScreen
     *   (docs/01_concept.md §6.17, повтор запроса бессмыслен).
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
            _uiState.value = UiState.Content(ChainUiState(session, maps))
        }
    }
}
