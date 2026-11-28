package com.zurus.cinethread.feature.game

import com.zurus.cinethread.core.common.Clock
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.core.common.SetupDepthPrefs
import com.zurus.cinethread.core.ui.UiState
import com.zurus.cinethread.data.pairsource.OnlinePairSource
import com.zurus.cinethread.domain.FakeGameSessionRepository
import com.zurus.cinethread.domain.actor
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.repository.GamePairRepository
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.usecase.FinishGameUseCase
import com.zurus.cinethread.domain.usecase.StartGameUseCase
import com.zurus.cinethread.domain.util.PairGenConfig
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
        val pair = com.zurus.cinethread.domain.model.GamePair(
            startActor = actor(1),
            targetActor = actor(2),
            shortestPath = emptyList(),
            seed = 7L,
        )
        val pairRepo = object : GamePairRepository {
            override suspend fun generatePair(
                seed: Long?,
                depth: Int,
            ): Result<GamePair, PairGenerationError> {
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

    private fun createViewModel(
        pair: GamePair?,
        pairError: PairGenerationError?,
        depthPrefs: SetupDepthPrefs = FakeSetupDepthPrefs(),
    ): GameSetupViewModel {
        val pairRepo = object : GamePairRepository {
            override suspend fun generatePair(
                seed: Long?,
                depth: Int,
            ): Result<GamePair, PairGenerationError> =
                if (pair != null) Result.Success(pair) else Result.Failure(requireNotNull(pairError))
        }
        return HomeSetupTestDoubles.viewModel(
            pairRepository = pairRepo,
            sessionRepository = FakeGameSessionRepository(),
            dispatcher = dispatcher,
            depthPrefs = depthPrefs,
        )
    }

    // ===== D5: выбор глубины пары (docs/debugging.md) =====

    @Test
    fun `depth defaults to 2`() {
        val viewModel = createViewModel(
            pair = GamePair(startActor = actor(1), targetActor = actor(2), shortestPath = emptyList(), seed = 7L),
            pairError = null,
        )
        assertEquals(2, viewModel.selectedDepth.value)
    }

    @Test
    fun `depth is restored from prefs`() {
        val prefs = FakeSetupDepthPrefs().apply { stored = 4 }
        val viewModel = createViewModel(
            pair = GamePair(startActor = actor(1), targetActor = actor(2), shortestPath = emptyList(), seed = 7L),
            pairError = null,
            depthPrefs = prefs,
        )
        assertEquals(4, viewModel.selectedDepth.value)
    }

    @Test
    fun `setDepth updates state and persists`() {
        val prefs = FakeSetupDepthPrefs()
        val viewModel = createViewModel(
            pair = GamePair(startActor = actor(1), targetActor = actor(2), shortestPath = emptyList(), seed = 7L),
            pairError = null,
            depthPrefs = prefs,
        )
        viewModel.setDepth(3)
        assertEquals(3, viewModel.selectedDepth.value)
        assertEquals("выбор обязан сохраниться", 3, prefs.stored)
    }

    @Test
    fun `setDepth clamps out-of-range values`() {
        val viewModel = createViewModel(
            pair = GamePair(startActor = actor(1), targetActor = actor(2), shortestPath = emptyList(), seed = 7L),
            pairError = null,
        )
        viewModel.setDepth(9)
        assertEquals("верхняя граница — 4", 4, viewModel.selectedDepth.value)
        viewModel.setDepth(1)
        assertEquals("нижняя граница — 2", 2, viewModel.selectedDepth.value)
    }

    @Test
    fun `start passes selected depth to use case`() = runTest {
        val prefs = FakeSetupDepthPrefs()
        var receivedDepth = -1
        val pairRepo = object : GamePairRepository {
            override suspend fun generatePair(
                seed: Long?,
                depth: Int,
            ): Result<GamePair, PairGenerationError> {
                receivedDepth = depth
                return Result.Success(
                    GamePair(startActor = actor(1), targetActor = actor(2), shortestPath = emptyList(), seed = 7L),
                )
            }
        }
        val viewModel = HomeSetupTestDoubles.viewModel(
            pairRepository = pairRepo,
            sessionRepository = FakeGameSessionRepository(),
            dispatcher = dispatcher,
            depthPrefs = prefs,
        )
        viewModel.setDepth(4)
        viewModel.start()
        assertEquals("выбранная глубина обязана дойти до use case", 4, receivedDepth)
    }
}

internal object HomeSetupTestDoubles {
    fun viewModel(
        pairRepository: GamePairRepository,
        sessionRepository: FakeGameSessionRepository,
        dispatcher: kotlinx.coroutines.test.TestDispatcher,
        depthPrefs: SetupDepthPrefs = FakeSetupDepthPrefs(),
    ): GameSetupViewModel =
        GameSetupViewModel(
            StartGameUseCase(
                // D8: use case зависит от PairSource; для тестов VM —
                // онлайн-адаптер над fake-репозиторием.
                OnlinePairSource(pairRepository),
                PairGenConfig(),
                sessionRepository,
                FinishGameUseCase(sessionRepository, Clock { 5000L }),
            ),
            depthPrefs,
            dispatcher,
        )
}

/** [SetupDepthPrefs] в памяти: хранит «сырое» значение [stored]. */
internal class FakeSetupDepthPrefs : SetupDepthPrefs {
    var stored: Int = SetupDepthPrefs.DEFAULT_DEPTH

    override var selectedDepth: Int
        get() = stored
        set(value) {
            stored = value.coerceIn(SetupDepthPrefs.MIN_DEPTH, SetupDepthPrefs.MAX_DEPTH)
        }
}
