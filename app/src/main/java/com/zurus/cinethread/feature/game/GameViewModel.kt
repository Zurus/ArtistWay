package com.zurus.cinethread.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.cinethread.core.common.IoDispatcher
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.core.ui.ChainResolver
import com.zurus.cinethread.core.ui.UiState
import com.zurus.cinethread.domain.model.GameResultReason
import com.zurus.cinethread.domain.model.GameSession
import com.zurus.cinethread.domain.model.GameSessionStatus
import com.zurus.cinethread.domain.repository.GameSessionRepository
import com.zurus.cinethread.domain.usecase.CheckDeadEndUseCase
import com.zurus.cinethread.domain.usecase.DeadEndStatus
import com.zurus.cinethread.domain.usecase.FinishGameUseCase
import com.zurus.cinethread.domain.usecase.UndoLastTransitionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран активной партии (docs/01_concept.md, §2.4; docs/05_roadmap.md, Этап 5):
 * восстановление партии из Room по [com.zurus.cinethread.domain.model.GameSession.sessionId],
 * отображение данных партии, отмена шага и сдача партии.
 *
 * Тупик (docs/04_algorithms.md §4): [CheckDeadEndUseCase] выполняется после
 * каждого зафиксированного перехода (переход заканчивается возвратом на экран
 * партии — `load` видит новый `stepCount` и проверяет тупик ровно один раз
 * на каждую глубину цепочки).
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val finishGameUseCase: FinishGameUseCase,
    private val checkDeadEndUseCase: CheckDeadEndUseCase,
    private val undoLastTransitionUseCase: UndoLastTransitionUseCase,
    private val chainResolver: ChainResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<GameUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<GameUiState>> = _uiState.asStateFlow()

    private var sessionId: String? = null

    /** Глубина цепочки, для которой уже выполнена проверка тупика. */
    private var checkedStepCount: Int? = null

    /** Загружает партию из Room; вызывается при каждом появлении экрана
     * (после process death и после возврата из Transition).
     *
     * @param onSessionNotFound партия не найдена (экран уходит на ErrorScreen,
     *   повтор запроса бессмыслен — docs/01_concept.md §6.17).
     */
    fun load(sessionId: String, onSessionNotFound: (() -> Unit)? = null) {
        this.sessionId = sessionId
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val session = withContext(ioDispatcher) { gameSessionRepository.findSession(sessionId) }
            if (session == null) {
                onSessionNotFound?.invoke()
                return@launch
            }
            val maps = withContext(ioDispatcher) { chainResolver.resolve(session) }
            val deadEnd = checkDeadEndIfNeeded(session)
            _uiState.value = UiState.Content(GameUiState(session, maps, deadEnd))
        }
    }

    /**
     * Отмена последнего шага (docs/04_algorithms.md §4.3):
     * `currentActorId` восстанавливается, цель не меняется, партия остаётся
     * `IN_PROGRESS`; сеть не используется (Room-данные).
     *
     * @return обновлённая партия либо `null`, если отменять нечего/сбой.
     */
    suspend fun undo(): GameSession? {
        val content = (_uiState.value as? UiState.Content)?.value ?: return null
        val updated = withContext(ioDispatcher) { undoLastTransitionUseCase(content.session) }
        return when (updated) {
            is Result.Success -> {
                if (updated.value == content.session) {
                    null
                } else {
                    checkedStepCount = updated.value.stepCount
                    _uiState.value =
                        UiState.Content(content.copy(session = updated.value, deadEndStatus = null))
                    updated.value
                }
            }
            is Result.Failure -> null
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

    /**
     * Проверка тупика после коммита (docs/04_algorithms.md §4.1):
     * выполняется один раз на каждую глубину цепочки и только для
     * незавершённых партий с непустой цепочкой (для пустой цепочки путь
     * гарантирован генератором пар, §3).
     */
    private suspend fun checkDeadEndIfNeeded(session: GameSession): DeadEndStatus? {
        if (session.status != GameSessionStatus.IN_PROGRESS) {
            return null
        }
        if (session.chain.isEmpty()) {
            return null
        }
        if (session.stepCount == checkedStepCount) {
            return null
        }
        checkedStepCount = session.stepCount
        return withContext(ioDispatcher) { checkDeadEndUseCase(session) }
    }

    private fun currentSession() =
        (_uiState.value as? UiState.Content)?.value?.session
}
