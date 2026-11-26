package com.zurus.cinethread.feature.actorpicker

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.ui.UiState
import com.zurus.cinethread.domain.FakeGameSessionRepository
import com.zurus.cinethread.domain.FakeGraph
import com.zurus.cinethread.domain.FakeMovieRepository
import com.zurus.cinethread.domain.actor
import com.zurus.cinethread.domain.sessionWith
import com.zurus.cinethread.domain.usecase.GetMovieCastUseCase
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

/** Тесты [ActorPickerViewModel]: каст без текущего актёра, пометка цели, ошибки (docs/05_roadmap.md, пункт 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class ActorPickerViewModelTest {

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

    private fun viewModel(
        sessionRepo: FakeGameSessionRepository,
        movieRepo: FakeMovieRepository,
    ): ActorPickerViewModel =
        ActorPickerViewModel(sessionRepo, movieRepo, GetMovieCastUseCase(movieRepo), dispatcher)

    @Test
    fun `cast filters out current actor`() = runTest {
        graph.edge(1, 101).edge(2, 101).edge(3, 101)
        val sessionRepo = FakeGameSessionRepository()
        sessionRepo.sessions["s"] = sessionWith(emptyList(), currentActorId = 1, sessionId = "s")
        val viewModel = viewModel(sessionRepo, FakeMovieRepository(graph))
        viewModel.load("s", 101)
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val content = (state as UiState.Content).value
        // Текущий актёр (1) исключён; цель (3) в списке и помечена.
        assertEquals(listOf(2, 3), content.actors.map { it.tmdbId })
        assertEquals(actor(3), content.targetActor)
        assertEquals("Фильм 101", content.movie?.title)
    }

    @Test
    fun `cast with only current actor - empty state`() = runTest {
        graph.edge(1, 101)
        val sessionRepo = FakeGameSessionRepository()
        sessionRepo.sessions["s"] = sessionWith(emptyList(), currentActorId = 1, sessionId = "s")
        val viewModel = viewModel(sessionRepo, FakeMovieRepository(graph))
        viewModel.load("s", 101)
        assertTrue(viewModel.uiState.value is UiState.Empty)
    }

    @Test
    fun `cast failure - error state`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        sessionRepo.sessions["s"] = sessionWith(emptyList(), currentActorId = 1, sessionId = "s")
        val movieRepo = FakeMovieRepository(graph, failWith = AppError.Offline)
        val viewModel = viewModel(sessionRepo, movieRepo)
        viewModel.load("s", 101)
        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `unknown session - onSessionNotFound invoked`() = runTest {
        val viewModel = viewModel(FakeGameSessionRepository(), FakeMovieRepository(graph))
        var notFoundInvoked = 0
        viewModel.load("missing", 101) { notFoundInvoked++ }
        assertEquals(1, notFoundInvoked)
        // Экран уходит на ErrorScreen — состояние остаётся Loading, а не Error.
        assertTrue(viewModel.uiState.value is UiState.Loading)
    }
}
