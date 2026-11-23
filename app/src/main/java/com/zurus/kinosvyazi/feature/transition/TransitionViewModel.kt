package com.zurus.kinosvyazi.feature.transition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.IoDispatcher
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.core.ui.ChainMaps
import com.zurus.kinosvyazi.core.ui.ChainResolver
import com.zurus.kinosvyazi.core.ui.ErrorMapper
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GameResultReason
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.usecase.CommitTransitionUseCase
import com.zurus.kinosvyazi.domain.usecase.FinishGameUseCase
import com.zurus.kinosvyazi.domain.usecase.ValidateTransitionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран подтверждения перехода (docs/01_concept.md, §2.7):
 * превью перехода, новая цепочка, «Подтвердить»/«Отменить».
 * Отмена не фиксирует шаг; победа проверяется после фиксации.
 */
@HiltViewModel
class TransitionViewModel @Inject constructor(
    private val gameSessionRepository: GameSessionRepository,
    private val actorRepository: ActorRepository,
    private val movieRepository: MovieRepository,
    private val validateTransitionUseCase: ValidateTransitionUseCase,
    private val commitTransitionUseCase: CommitTransitionUseCase,
    private val finishGameUseCase: FinishGameUseCase,
    private val chainResolver: ChainResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<TransitionUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<TransitionUiState>> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var movieId: Int = 0
    private var toActorId: Int = 0

    /** @param onSessionNotFound партия не найдена — экран уходит на ErrorScreen
     *   (docs/01_concept.md §6.17). */
    fun load(sessionId: String, movieId: Int, toActorId: Int, onSessionNotFound: (() -> Unit)? = null) {
        this.sessionId = sessionId
        this.movieId = movieId
        this.toActorId = toActorId
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val session = withContext(ioDispatcher) { gameSessionRepository.findSession(sessionId) }
            if (session == null) {
                onSessionNotFound?.invoke()
                return@launch
            }
            val (movie, toActor) = withContext(ioDispatcher) {
                val movieResult = movieRepository.getMovie(movieId)
                val actorResult = actorRepository.getActor(toActorId)
                val movieValue = when (movieResult) {
                    is Result.Success -> movieResult.value
                    is Result.Failure -> null
                }
                val actorValue = when (actorResult) {
                    is Result.Success -> actorResult.value
                    is Result.Failure -> null
                }
                Pair(movieValue, actorValue)
            }
            if (toActor == null) {
                _uiState.value = UiState.Error(ErrorMapper.map(AppError.DataInvalid("Актёр не найден")))
                return@launch
            }
            val candidate = ChainStep(
                stepIndex = session.stepCount + 1,
                fromActorId = session.currentActor.tmdbId,
                movieId = movieId,
                toActorId = toActorId,
            )
            val chainMaps = withContext(ioDispatcher) { chainResolver.resolve(session) }
            when (val validation = withContext(ioDispatcher) {
                validateTransitionUseCase(session, movieId, toActorId)
            }) {
                is Result.Success -> {
                    _uiState.value = UiState.Content(
                        TransitionUiState(
                            session = session,
                            fromActor = session.currentActor,
                            movie = movie,
                            toActor = toActor,
                            candidateStep = candidate,
                            chain = chainMaps,
                            validation = ResultValidation.fromValidation(validation.value),
                        ),
                    )
                }
                is Result.Failure ->
                    _uiState.value = UiState.Error(ErrorMapper.map(validation.error))
            }
        }
    }

    /**
     * Фиксирует переход.
     *
     * Контракт [CommitTransitionUseCase]: сам он партию не завершает —
     * при достижении цели UI дополнительно вызывает [FinishGameUseCase] (WIN).
     *
     * @return результат фиксации для навигации.
     */
    suspend fun confirm(): ConfirmOutcome {
        val content = (_uiState.value as? UiState.Content)?.value ?: return ConfirmOutcome.Failure
        return when (val result = withContext(ioDispatcher) {
            commitTransitionUseCase(content.session, movieId, toActorId)
        }) {
            is Result.Success -> {
                val updated = result.value
                if (updated.isWin) {
                    when (val finish = withContext(ioDispatcher) {
                        finishGameUseCase(updated.session, GameResultReason.WIN)
                    }) {
                        is Result.Success -> {
                            // Экран результата перечитает партию из Room;
                            // здесь держим закоммиченный (не завершённый) снэпшот.
                            _uiState.value =
                                UiState.Content(content.copy(session = updated.session))
                            ConfirmOutcome.Win
                        }
                        is Result.Failure -> {
                            // Шаг зафиксирован, но партию не удалось закрыть
                            // (сбой сети в момент победы). Ошибка + повтор подтвердит
                            // уже следующий переход (актёр уже на цели).
                            _uiState.value = UiState.Error(ErrorMapper.map(finish.error))
                            ConfirmOutcome.Failure
                        }
                    }
                } else {
                    _uiState.value = UiState.Content(content.copy(session = updated.session))
                    ConfirmOutcome.Ok
                }
            }
            is Result.Failure -> {
                _uiState.value = UiState.Error(ErrorMapper.map(result.error))
                ConfirmOutcome.Failure
            }
        }
    }
}

/** Исход подтверждения перехода для навигации. */
sealed interface ConfirmOutcome {
    /** Переход зафиксирован, партия продолжается. */
    data object Ok : ConfirmOutcome

    /** Переход зафиксирован, цель достигнута. */
    data object Win : ConfirmOutcome

    /** Фиксация не удалась — остаёмся на экране. */
    data object Failure : ConfirmOutcome
}
