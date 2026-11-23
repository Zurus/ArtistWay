package com.zurus.kinosvyazi.feature.result

import com.zurus.kinosvyazi.core.ui.ChainResolver
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeMovieRepository
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GameSessionStatus
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

/** Тесты [GameResultViewModel] и [GameResultNonWinViewModel] (docs/05_roadmap.md, пункт 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class GameResultViewModelTest {

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

    private val chain = listOf(
        ChainStep(1, 1, 101, 2),
        ChainStep(2, 2, 102, 3),
    )

    private fun wonSession() = sessionWith(chain, currentActorId = 3, sessionId = "s").copy(
        status = GameSessionStatus.WON,
        finishedAtMillis = 9000L,
    )

    @Test
    fun `win result - content with chain and metrics`() = runTest {
        graph.edge(1, 101).edge(2, 101).edge(2, 102).edge(3, 102)
        val sessionRepo = FakeGameSessionRepository()
        sessionRepo.sessions["s"] = wonSession()
        val viewModel = GameResultViewModel(
            sessionRepo,
            ChainResolver(FakeActorRepository(graph), FakeMovieRepository(graph)),
            dispatcher,
        )
        viewModel.load("s")
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val content = (state as UiState.Content).value
        assertEquals(2, content.session.stepCount)
        assertEquals(9000L, content.session.finishedAtMillis)
        assertEquals(2, content.chain.movies.size)
        assertEquals("00:09", formatGameDuration(content.session.startedAtMillis, content.session.finishedAtMillis))
    }

    @Test
    fun `non-win result - content with status`() = runTest {
        graph.edge(1, 101).edge(2, 101)
        val sessionRepo = FakeGameSessionRepository()
        sessionRepo.sessions["s"] = sessionWith(
            listOf(ChainStep(1, 1, 101, 2)),
            currentActorId = 2,
            sessionId = "s",
        ).copy(
            status = GameSessionStatus.SURRENDERED,
            finishedAtMillis = 4000L,
        )
        val viewModel = GameResultNonWinViewModel(
            sessionRepo,
            ChainResolver(FakeActorRepository(graph), FakeMovieRepository(graph)),
            dispatcher,
        )
        viewModel.load("s")
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val content = (state as UiState.Content).value
        assertEquals(GameSessionStatus.SURRENDERED, content.session.status)
        assertEquals(1, content.session.stepCount)
    }

    @Test
    fun `unknown session - onSessionNotFound invoked for both screens`() = runTest {
        val winViewModel = GameResultViewModel(
            FakeGameSessionRepository(),
            ChainResolver(FakeActorRepository(graph), FakeMovieRepository(graph)),
            dispatcher,
        )
        var winNotFound = 0
        winViewModel.load("missing") { winNotFound++ }
        assertEquals(1, winNotFound)
        assertTrue(winViewModel.uiState.value is UiState.Loading)

        val nonWinViewModel = GameResultNonWinViewModel(
            FakeGameSessionRepository(),
            ChainResolver(FakeActorRepository(graph), FakeMovieRepository(graph)),
            dispatcher,
        )
        var nonWinNotFound = 0
        nonWinViewModel.load("missing") { nonWinNotFound++ }
        assertEquals(1, nonWinNotFound)
        assertTrue(nonWinViewModel.uiState.value is UiState.Loading)
    }

    @Test
    fun `duration formatting edge cases`() {
        assertEquals("00:00", formatGameDuration(1000L, 1000L))
        assertEquals("01:00", formatGameDuration(0L, 60_000L))
        assertEquals("00:00", formatGameDuration(5000L, 1000L))
    }
}
