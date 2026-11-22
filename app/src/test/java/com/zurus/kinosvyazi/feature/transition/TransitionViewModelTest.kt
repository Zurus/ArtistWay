package com.zurus.kinosvyazi.feature.transition

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Clock
import com.zurus.kinosvyazi.core.ui.ChainResolver
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeMovieRepository
import com.zurus.kinosvyazi.domain.sessionWith
import com.zurus.kinosvyazi.domain.usecase.CommitTransitionUseCase
import com.zurus.kinosvyazi.domain.usecase.FinishGameUseCase
import com.zurus.kinosvyazi.domain.usecase.ValidateTransitionUseCase
import com.zurus.kinosvyazi.domain.util.Budget
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

/** Тесты [TransitionViewModel]: валидация, подтверждение, победа (docs/05_roadmap.md, пункт 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class TransitionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val graph = FakeGraph()

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    /** Сессия: старт 1, цель 3; фильм 101 с кастом [1, 2, 3]; текущий — 1. */
    private fun setupSession(sessionRepo: FakeGameSessionRepository) {
        graph.edge(1, 101).edge(2, 101).edge(3, 101)
        sessionRepo.sessions["s"] = sessionWith(emptyList(), currentActorId = 1, sessionId = "s")
    }

    private fun createViewModel(
        sessionRepo: FakeGameSessionRepository,
        graph: FakeGraph = this.graph,
    ): TransitionViewModel {
        val actorRepo = FakeActorRepository(graph)
        val movieRepo = FakeMovieRepository(graph)
        val budgetFactory = { Budget() }
        return TransitionViewModel(
            sessionRepo,
            actorRepo,
            movieRepo,
            ValidateTransitionUseCase(actorRepo, movieRepo, budgetFactory),
            CommitTransitionUseCase(sessionRepo, actorRepo, movieRepo, budgetFactory),
            FinishGameUseCase(sessionRepo, Clock { 9000L }),
            ChainResolver(actorRepo, movieRepo),
            dispatcher,
        )
    }

    @Test
    fun `valid transition to non-target - content with Valid`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        setupSession(sessionRepo)
        val viewModel = createViewModel(sessionRepo)
        viewModel.load("s", 101, 2)
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val content = (state as UiState.Content).value
        assertEquals(2, content.toActor.tmdbId)
        assertTrue(content.validation is ResultValidation.Valid)
    }

    @Test
    fun `actor not in cast - content with Invalid`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        setupSession(sessionRepo)
        // Актёр 9 нет в касте фильма 101.
        val viewModel = createViewModel(sessionRepo)
        viewModel.load("s", 101, 9)
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val validation = (state as UiState.Content).value.validation
        assertTrue(validation is ResultValidation.Invalid)
    }

    @Test
    fun `confirm non-target transition - Ok and session updated`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        setupSession(sessionRepo)
        val viewModel = createViewModel(sessionRepo)
        viewModel.load("s", 101, 2)
        assertEquals(ConfirmOutcome.Ok, viewModel.confirm())
        val updated = sessionRepo.sessions["s"]!!
        assertEquals(2, updated.currentActor.tmdbId)
        assertEquals(1, updated.stepCount)
    }

    @Test
    fun `confirm target transition - Win`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        setupSession(sessionRepo)
        val viewModel = createViewModel(sessionRepo)
        viewModel.load("s", 101, 3)
        assertEquals(ConfirmOutcome.Win, viewModel.confirm())
        assertEquals(
            com.zurus.kinosvyazi.domain.model.GameSessionStatus.WON,
            sessionRepo.sessions["s"]!!.status,
        )
    }

    @Test
    fun `confirm when commit fails - Failure`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        setupSession(sessionRepo)
        sessionRepo.commitFail = AppError.Offline
        val viewModel = createViewModel(sessionRepo)
        viewModel.load("s", 101, 2)
        assertEquals(ConfirmOutcome.Failure, viewModel.confirm())
    }

    @Test
    fun `unknown session - error state`() = runTest {
        val viewModel = createViewModel(FakeGameSessionRepository())
        viewModel.load("missing", 101, 2)
        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
