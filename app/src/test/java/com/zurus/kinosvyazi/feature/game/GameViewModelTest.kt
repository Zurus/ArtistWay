package com.zurus.kinosvyazi.feature.game

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Clock
import com.zurus.kinosvyazi.core.ui.ChainResolver
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeMovieRepository
import com.zurus.kinosvyazi.domain.actor
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GameResultReason
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.model.GameSessionStatus
import com.zurus.kinosvyazi.domain.usecase.CheckDeadEndUseCase
import com.zurus.kinosvyazi.domain.usecase.DeadEndCause
import com.zurus.kinosvyazi.domain.usecase.DeadEndStatus
import com.zurus.kinosvyazi.domain.usecase.FinishGameUseCase
import com.zurus.kinosvyazi.domain.usecase.UndoLastTransitionUseCase
import com.zurus.kinosvyazi.domain.util.Budget
import com.zurus.kinosvyazi.domain.util.PairGenConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты [GameViewModel]: загрузка из Room, сдача партии (docs/05_roadmap.md, пункт 4),
 * проверка тупика после коммита и отмена шага (docs/05_roadmap.md, Этап 5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val graph = FakeGraph()
    private val actorRepo = FakeActorRepository(graph)

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    /**
     * Партия: 1 → 11 → 3, цель 2. В этом графе пути от 3 к цели 2 нет
     * (тесты тупика), поэтому [createViewModel] принимает отдельный graph.
     */
    private fun inProgressSession(sessionId: String = "s-1"): GameSession =
        GameSession(
            sessionId = sessionId,
            startActor = actor(1),
            targetActor = actor(2),
            currentActor = actor(3),
            chain = listOf(ChainStep(1, 1, 11, 3)),
            status = GameSessionStatus.IN_PROGRESS,
            startedAtMillis = 1000L,
            updatedAtMillis = 2000L,
            finishedAtMillis = null,
            stepCount = 1,
            shortestPath = null,
            generationSeed = null,
        )

    private fun freshSession(sessionId: String = "s-fresh"): GameSession =
        GameSession(
            sessionId = sessionId,
            startActor = actor(1),
            targetActor = actor(2),
            currentActor = actor(1),
            chain = emptyList(),
            status = GameSessionStatus.IN_PROGRESS,
            startedAtMillis = 1000L,
            updatedAtMillis = 1000L,
            finishedAtMillis = null,
            stepCount = 0,
            shortestPath = null,
            generationSeed = null,
        )

    @Test
    fun `load existing session - content with resolved chain and dead-end check`() = runTest {
        graph.edge(1, 11).edge(3, 11).edge(2, 12)
        val sessionRepo = FakeGameSessionRepository()
        val s = inProgressSession()
        sessionRepo.sessions[s.sessionId] = s
        val viewModel = createViewModel(sessionRepo)
        viewModel.load(s.sessionId)
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val content = (state as UiState.Content).value
        assertEquals(s, content.session)
        assertEquals("Фильм 11", content.chain.movies[11]?.title)
        assertEquals("Актёр 1", content.chain.actors[1]?.name)
        // docs/04_algorithms.md §4.2: после коммита проверка тупика выполнена.
        assertEquals(DeadEndStatus.DeadEnd(DeadEndCause.NO_PATH), content.deadEndStatus)
    }

    @Test
    fun `load fresh session - no dead-end check for empty chain`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        val s = freshSession()
        sessionRepo.sessions[s.sessionId] = s
        val viewModel = createViewModel(sessionRepo)
        viewModel.load(s.sessionId)
        val content = (viewModel.uiState.value as UiState.Content).value
        // docs/04_algorithms.md §3: для пустой цепочки путь гарантирован генератором.
        assertNull(content.deadEndStatus)
    }

    @Test
    fun `load detects no-movies dead end`() = runTest {
        // Текущий актёр 5 ни в каких фильмах.
        val sessionRepo = FakeGameSessionRepository()
        val s = freshSession().copy(currentActor = actor(5))
        sessionRepo.sessions[s.sessionId] = s
        val viewModel = createViewModel(sessionRepo)
        viewModel.load(s.sessionId)
        // Пустая цепочка — проверка не выполняется (§3):
        assertNull((viewModel.uiState.value as UiState.Content).value.deadEndStatus)

        // После «коммита» (stepCount > 0) с актёром без фильмов — NO_MOVIES.
        val withStep = s.copy(
            chain = listOf(ChainStep(1, 1, 11, 5)),
            currentActor = actor(5),
            stepCount = 1,
        )
        sessionRepo.sessions[s.sessionId] = withStep
        viewModel.load(s.sessionId)
        assertEquals(
            DeadEndStatus.DeadEnd(DeadEndCause.NO_MOVIES),
            (viewModel.uiState.value as UiState.Content).value.deadEndStatus,
        )
    }

    @Test
    fun `load detects ok status with remaining path`() = runTest {
        // 3 → 13 → 2: цель достижима одним переходом.
        graph.edge(1, 11).edge(3, 11).edge(3, 13).edge(2, 13)
        val sessionRepo = FakeGameSessionRepository()
        val s = inProgressSession()
        sessionRepo.sessions[s.sessionId] = s
        val viewModel = createViewModel(sessionRepo)
        viewModel.load(s.sessionId)
        assertEquals(
            DeadEndStatus.Ok(1),
            (viewModel.uiState.value as UiState.Content).value.deadEndStatus,
        )
    }

    @Test
    fun `load unknown session invokes onSessionNotFound`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        val viewModel = createViewModel(sessionRepo)
        var notFoundInvoked = 0
        viewModel.load("missing") { notFoundInvoked++ }
        assertEquals(1, notFoundInvoked)
        // Экран уходит на ErrorScreen — состояние остаётся Loading, а не Error.
        assertTrue(viewModel.uiState.value is UiState.Loading)
    }

    @Test
    fun `surrender finishes session and returns true`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        val s = inProgressSession()
        sessionRepo.sessions[s.sessionId] = s
        val viewModel = createViewModel(sessionRepo)
        viewModel.load(s.sessionId)
        assertTrue(viewModel.surrender())
        assertEquals(GameSessionStatus.SURRENDERED, sessionRepo.sessions[s.sessionId]!!.status)
        assertEquals(GameResultReason.SURRENDER, sessionRepo.finished.last().reason)
    }

    @Test
    fun `surrender failure returns false`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        val s = inProgressSession()
        sessionRepo.sessions[s.sessionId] = s
        sessionRepo.finishFail = AppError.Offline
        val viewModel = createViewModel(sessionRepo)
        viewModel.load(s.sessionId)
        assertFalse(viewModel.surrender())
    }

    @Test
    fun `surrender without load returns false`() = runTest {
        val viewModel = createViewModel(FakeGameSessionRepository())
        assertFalse(viewModel.surrender())
    }

    @Test
    fun `undo restores previous actor and step count without data requests`() = runTest {
        graph.edge(1, 11).edge(3, 11)
        val sessionRepo = FakeGameSessionRepository()
        val s = inProgressSession()
        sessionRepo.sessions[s.sessionId] = s
        val viewModel = createViewModel(sessionRepo)
        viewModel.load(s.sessionId)
        val callsAfterLoad = actorRepo.movieCalls

        val updated = viewModel.undo()
        requireNotNull(updated) { "undo должен вернуть обновлённую сессию" }
        assertEquals(actor(1), updated.currentActor)
        assertEquals(0, updated.stepCount)
        assertTrue(updated.chain.isEmpty())
        assertEquals("партия остаётся IN_PROGRESS", GameSessionStatus.IN_PROGRESS, updated.status)
        // docs/04_algorithms.md §4.4: undo не использует сеть (только Room).
        assertEquals(callsAfterLoad, actorRepo.movieCalls)
        // Состояние экрана обновлено.
        val content = (viewModel.uiState.value as UiState.Content).value
        assertEquals(actor(1), content.session.currentActor)
    }

    @Test
    fun `undo on empty chain returns null and keeps state`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        val s = freshSession()
        sessionRepo.sessions[s.sessionId] = s
        val viewModel = createViewModel(sessionRepo)
        viewModel.load(s.sessionId)
        val stateBefore = viewModel.uiState.value

        val updated = viewModel.undo()

        assertNull(updated)
        assertEquals(stateBefore, viewModel.uiState.value)
    }

    private fun createViewModel(
        sessionRepo: FakeGameSessionRepository,
        actorRepo: FakeActorRepository = this.actorRepo,
    ): GameViewModel {
        val clock = Clock { 5000L }
        return GameViewModel(
            sessionRepo,
            FinishGameUseCase(sessionRepo, clock),
            CheckDeadEndUseCase(
                actorRepo,
                FakeMovieRepository(graph),
                PairGenConfig(),
                { Budget() },
            ),
            UndoLastTransitionUseCase(sessionRepo),
            ChainResolver(actorRepo, FakeMovieRepository(graph)),
            dispatcher,
        )
    }
}
