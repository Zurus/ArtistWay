package com.zurus.kinosvyazi.feature.moviepicker

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.actor
import com.zurus.kinosvyazi.domain.model.Movie
import com.zurus.kinosvyazi.domain.sessionWith
import com.zurus.kinosvyazi.domain.usecase.GetActorMoviesUseCase
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

/** Тесты [MoviePickerViewModel]: список фильмов, пустое состояние, ошибка (docs/05_roadmap.md, пункт 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class MoviePickerViewModelTest {

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
        actorRepo: FakeActorRepository,
    ): MoviePickerViewModel =
        MoviePickerViewModel(sessionRepo, GetActorMoviesUseCase(actorRepo), dispatcher)

    @Test
    fun `actor with movies - content with list`() = runTest {
        graph.edge(1, 101).edge(1, 102)
        val sessionRepo = FakeGameSessionRepository()
        sessionRepo.sessions["s"] = sessionWith(emptyList(), currentActorId = 1, sessionId = "s")
        val viewModel = viewModel(sessionRepo, FakeActorRepository(graph))
        viewModel.load("s")
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val content = (state as UiState.Content).value
        assertEquals(actor(1), content.currentActor)
        assertEquals(listOf(101, 102), content.movies.map { it.tmdbId })
    }

    @Test
    fun `actor without movies - empty state`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        sessionRepo.sessions["s"] = sessionWith(emptyList(), currentActorId = 1, sessionId = "s")
        val viewModel = viewModel(sessionRepo, FakeActorRepository(graph))
        viewModel.load("s")
        assertTrue(viewModel.uiState.value is UiState.Empty)
    }

    @Test
    fun `repository failure - error state`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        sessionRepo.sessions["s"] = sessionWith(emptyList(), currentActorId = 1, sessionId = "s")
        val actorRepo = FakeActorRepository(graph, failWith = AppError.Offline)
        val viewModel = viewModel(sessionRepo, actorRepo)
        viewModel.load("s")
        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `unknown session - error state`() = runTest {
        val viewModel = viewModel(FakeGameSessionRepository(), FakeActorRepository(graph))
        viewModel.load("missing")
        assertTrue(viewModel.uiState.value is UiState.Error)
    }
}
