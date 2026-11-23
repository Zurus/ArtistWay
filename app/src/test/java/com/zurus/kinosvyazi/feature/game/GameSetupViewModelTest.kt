package com.zurus.kinosvyazi.feature.game

import com.zurus.kinosvyazi.core.common.Clock
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.actor
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.repository.GamePairRepository
import com.zurus.kinosvyazi.domain.repository.PairGenerationError
import com.zurus.kinosvyazi.domain.usecase.FinishGameUseCase
import com.zurus.kinosvyazi.domain.usecase.StartGameUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Тесты [GameSetupViewModel]: генерация партии и ошибки (docs/05_roadmap.md, пункт 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class GameSetupViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `successful start - content with session`() = runTest {
        val viewModel = createViewModel(
            pair = GamePair(startActor = actor(1), targetActor = actor(2), shortestPath = emptyList(), seed = 7L),
            pairError = null,
        )
        viewModel.start()
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val session = (state as UiState.Content).value.session
        assertEquals(1, session.startActor.tmdbId)
        assertEquals(2, session.targetActor.tmdbId)
        assertTrue(session.sessionId.isNotBlank())
    }

    @Test
    fun `budget exceeded - error state`() = runTest {
        val viewModel = createViewModel(
            pair = null,
            pairError = PairGenerationError.BudgetExceeded("requests"),
        )
        viewModel.start()
        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `no valid pair - error state`() = runTest {
        val viewModel = createViewModel(
            pair = null,
            pairError = PairGenerationError.NoValidPair,
        )
        viewModel.start()
        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `start while loading is ignored`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        val pair = com.zurus.kinosvyazi.domain.model.GamePair(
            startActor = actor(1),
            targetActor = actor(2),
            shortestPath = emptyList(),
            seed = 7L,
        )
        val pairRepo = object : GamePairRepository {
            override suspend fun generatePair(seed: Long?): Result<GamePair, PairGenerationError> {
                calls++
                gate.await()
                return Result.Success(pair)
            }
        }
        val viewModel = HomeSetupTestDoubles.viewModel(
            pairRepository = pairRepo,
            sessionRepository = FakeGameSessionRepository(),
            dispatcher = dispatcher,
        )
        viewModel.start()
        assertEquals(1, calls)
        // Пока первая генерация ещё в работе — повторный вызов игнорируется.
        viewModel.start()
        assertEquals(1, calls)
        gate.complete(Unit)
        assertTrue((viewModel.uiState.value as UiState.Content).value.session.sessionId.isNotBlank())
    }

    private fun createViewModel(pair: GamePair?, pairError: PairGenerationError?): GameSetupViewModel {
        val pairRepo = object : GamePairRepository {
            override suspend fun generatePair(seed: Long?): Result<GamePair, PairGenerationError> =
                if (pair != null) Result.Success(pair) else Result.Failure(requireNotNull(pairError))
        }
        return HomeSetupTestDoubles.viewModel(
            pairRepository = pairRepo,
            sessionRepository = FakeGameSessionRepository(),
            dispatcher = dispatcher,
        )
    }
}

internal object HomeSetupTestDoubles {
    fun viewModel(
        pairRepository: GamePairRepository,
        sessionRepository: FakeGameSessionRepository,
        dispatcher: kotlinx.coroutines.test.TestDispatcher,
    ): GameSetupViewModel =
        GameSetupViewModel(
            StartGameUseCase(
                pairRepository,
                sessionRepository,
                FinishGameUseCase(sessionRepository, Clock { 5000L }),
            ),
            dispatcher,
        )
}
