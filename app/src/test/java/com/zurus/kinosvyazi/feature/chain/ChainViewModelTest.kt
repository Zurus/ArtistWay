package com.zurus.kinosvyazi.feature.chain

import com.zurus.kinosvyazi.core.ui.ChainResolver
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeMovieRepository
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.sessionWith
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

/** Тесты [ChainViewModel]: просмотр цепочки (docs/05_roadmap.md, пункт 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChainViewModelTest {

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

    @Test
    fun `load session - content with resolved actors and movies`() = runTest {
        val chain = listOf(
            ChainStep(1, 1, 101, 2),
            ChainStep(2, 2, 102, 3),
        )
        graph.edge(1, 101).edge(2, 101).edge(2, 102).edge(3, 102)
        val sessionRepo = FakeGameSessionRepository()
        sessionRepo.sessions["s"] = sessionWith(chain, currentActorId = 3, sessionId = "s")
        val viewModel = ChainViewModel(
            sessionRepo,
            ChainResolver(FakeActorRepository(graph), FakeMovieRepository(graph)),
            dispatcher,
        )
        viewModel.load("s")
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val content = (state as UiState.Content).value
        assertEquals(2, content.session.stepCount)
        // Старт (1), цель (3), текущий (3), актёры шагов (1, 2, 3).
        assertEquals(setOf(1, 2, 3), content.chain.actors.keys)
        assertEquals(setOf(101, 102), content.chain.movies.keys)
    }

    @Test
    fun `unknown session - onSessionNotFound invoked`() = runTest {
        val viewModel = ChainViewModel(
            FakeGameSessionRepository(),
            ChainResolver(FakeActorRepository(graph), FakeMovieRepository(graph)),
            dispatcher,
        )
        var notFoundInvoked = 0
        viewModel.load("missing") { notFoundInvoked++ }
        assertEquals(1, notFoundInvoked)
        // Экран уходит на ErrorScreen — состояние остаётся Loading, а не Error.
        assertTrue(viewModel.uiState.value is UiState.Loading)
    }
}
