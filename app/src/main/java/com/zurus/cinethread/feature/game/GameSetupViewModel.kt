package com.zurus.cinethread.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.cinethread.R
import com.zurus.cinethread.core.common.IoDispatcher
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.core.ui.ErrorMapper
import com.zurus.cinethread.core.ui.UiState
import com.zurus.cinethread.core.ui.UiText
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.usecase.StartGameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран генерации партии (docs/01_concept.md, §2.3):
 * «Генерируем пару…» → экран активной партии;
 * при ошибке — сообщение и кнопка «Повторить».
 */
@HiltViewModel
class GameSetupViewModel @Inject constructor(
    private val startGameUseCase: StartGameUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<GameSetupUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<GameSetupUiState>> = _uiState.asStateFlow()

    /** true, пока генерация идёт (начальное Loading не считается «идущей»). */
    private var inProgress = false

    /** Запускает (или повторяет) генерацию партии. */
    fun start() {
        if (_uiState.value is UiState.Content) return
        if (inProgress) return
        inProgress = true
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                when (val result = withContext(ioDispatcher) { startGameUseCase() }) {
                    is Result.Success ->
                        _uiState.value = UiState.Content(GameSetupUiState(result.value))
                    is Result.Failure ->
                        // docs/01_concept.md §6.1/6.3: конкретные тексты
                        // «Не удалось подобрать пару» / «Лимит запросов исчерпан».
                        _uiState.value = UiState.Error(result.error.toUiText())
                }
            } finally {
                inProgress = false
            }
        }
    }

    /**
     * Ошибки генерации парами (docs/01_concept.md §6.1/6.3) — не «некорректные данные»:
     * [PairGenerationError.NoValidPair] → «Не удалось подобрать подходящую пару»,
     * [PairGenerationError.BudgetExceeded] → «Лимит запросов исчерпан».
     */
    private fun PairGenerationError.toUiText(): UiText = when (this) {
        PairGenerationError.NoValidPair -> UiText.Resource(R.string.setup_error_no_pair)
        is PairGenerationError.BudgetExceeded -> UiText.Resource(R.string.setup_error_budget)
        is PairGenerationError.DataError -> ErrorMapper.map(error)
    }
}
