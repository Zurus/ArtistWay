package com.zurus.cinethread.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.cinethread.R
import com.zurus.cinethread.core.common.IoDispatcher
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.core.common.SetupDepthPrefs
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
 * Экран подготовки к игре (docs/01_concept.md, §2.3; docs/debugging.md D5):
 * выбор глубины пары 2/3/4 (по умолчанию 2, выбор сохраняется в
 * SharedPreferences) → «Подбираем пару глубины N…» → экран активной
 * партии; при ошибке — сообщение и «Повторить».
 */
@HiltViewModel
class GameSetupViewModel @Inject constructor(
    private val startGameUseCase: StartGameUseCase,
    private val setupDepthPrefs: SetupDepthPrefs,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<GameSetupUiState>>(UiState.Empty)
    val uiState: StateFlow<UiState<GameSetupUiState>> = _uiState.asStateFlow()

    /**
     * Выбранная глубина пары (D5): по умолчанию 2, восстанавливается из
     * [SetupDepthPrefs] (выбор сохраняется между запусками).
     */
    private val _selectedDepth = MutableStateFlow(setupDepthPrefs.selectedDepth)
    val selectedDepth: StateFlow<Int> = _selectedDepth.asStateFlow()

    /** true, пока идёт генерация (начальный Loading не считается). */
    private var inProgress = false

    /**
     * Устанавливает глубину пары: 2..4 (значение вне диапазона
     * приводится к ближайшему допустимому), выбор сохраняется
     * (D5: SharedPreferences).
     */
    fun setDepth(depth: Int) {
        val normalized = depth.coerceIn(SetupDepthPrefs.MIN_DEPTH, SetupDepthPrefs.MAX_DEPTH)
        setupDepthPrefs.selectedDepth = normalized
        _selectedDepth.value = normalized
    }

    /** Запускает (или повторяет) генерацию партии на выбранной глубине. */
    fun start() {
        if (_uiState.value is UiState.Content) return
        if (inProgress) return
        val depth = _selectedDepth.value
        inProgress = true
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                when (val result = withContext(ioDispatcher) { startGameUseCase(depth = depth) }) {
                    is Result.Success ->
                        _uiState.value = UiState.Content(GameSetupUiState(result.value))
                    is Result.Failure ->
                        _uiState.value = UiState.Error(result.error.toUiText())
                }
            } finally {
                inProgress = false
            }
        }
    }

    private fun PairGenerationError.toUiText(): UiText = when (this) {
        is PairGenerationError.BudgetExceeded ->
            UiText.Resource(R.string.setup_error_budget)
        PairGenerationError.NoValidPair ->
            UiText.Resource(R.string.setup_error_no_pair)
        is PairGenerationError.DataError ->
            ErrorMapper.map(error)
    }
}
