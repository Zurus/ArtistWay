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
import com.zurus.kinosvyazi.domain.usecase.FinishGameUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Тесты [GameViewModel]: загрузка из Room, сдача партии (docs/05_roadmap.md, пункт 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

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

    private fun inProgressSession(): GameSession {
        graph.edge(1, 11).edge(3, 11)
        graph.edge(2, 12)
        return GameSession(
            sessionId = "s-1",
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
    }

    @Test
    fun `load existing session - content with resolved chain`() = runTest {
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
    }

    @Test
    fun `load unknown session - error`() = runTest {
        val sessionRepo = FakeGameSessionRepository()
        val viewModel = createViewModel(sessionRepo)
        viewModel.load("missing")
        assertTrue(viewModel.uiState.value is UiState.Error)
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

    private fun createViewModel(sessionRepo: FakeGameSessionRepository): GameViewModel {
        val clock = Clock { 5000L }
        return GameViewModel(
            sessionRepo,
            FinishGameUseCase(sessionRepo, clock),
            ChainResolver(FakeActorRepository(graph), FakeMovieRepository(graph)),
            dispatcher,
        )
    }
}
