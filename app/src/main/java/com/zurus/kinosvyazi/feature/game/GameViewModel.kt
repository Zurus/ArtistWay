package com.zurus.kinosvyazi.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.IoDispatcher
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.core.ui.ChainResolver
import com.zurus.kinosvyazi.core.ui.ErrorMapper
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.model.GameResultReason
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.usecase.FinishGameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран активной партии (docs/01_concept.md, §2.4, docs/05_roadmap.md, пункт 4):
 * восстановление партии из Room по [com.zurus.kinosvyazi.domain.model.GameSession.sessionId],
 * отображение данных партии, сдача партии.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val finishGameUseCase: FinishGameUseCase,
    private val chainResolver: ChainResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<GameUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<GameUiState>> = _uiState.asStateFlow()

    private var sessionId: String? = null

    /** Загружает партию из Room; вызывается при каждом появлении экрана
     * (после process death и после возврата из Transition). */
    fun load(sessionId: String) {
        this.sessionId = sessionId
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val session = withContext(ioDispatcher) { gameSessionRepository.findSession(sessionId) }
            if (session == null) {
                _uiState.value = UiState.Error(ErrorMapper.map(AppError.DataInvalid("Партия не найдена")))
                return@launch
            }
            val maps = withContext(ioDispatcher) { chainResolver.resolve(session) }
            _uiState.value = UiState.Content(GameUiState(session, maps))
        }
    }

    /**
     * Завершает партию со сдачей.
     *
     * @return `true`, если партия завершена (экран результата) либо уже
     *   завершена ранее (дубль вызова) — навигация в любом случае корректна.
     */
    suspend fun surrender(): Boolean {
        val current = currentSession() ?: return false
        val result = withContext(ioDispatcher) {
            finishGameUseCase(current, GameResultReason.SURRENDER)
        }
        return when (result) {
            is Result.Success -> true
            is Result.Failure -> false
        }
    }

    private fun currentSession() =
        (_uiState.value as? UiState.Content)?.value?.session
}
