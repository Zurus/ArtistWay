package com.zurus.kinosvyazi.domain.usecase

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.FakeActorRepository
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeMovieRepository
import com.zurus.kinosvyazi.domain.actor
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.model.GameSessionStatus
import com.zurus.kinosvyazi.domain.util.Budget
import com.zurus.kinosvyazi.domain.util.ChainInvalidReason
import com.zurus.kinosvyazi.domain.util.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * ValidateTransition/CommitTransition/UndoLastTransition
 * (docs/04_algorithms.md §2.4, §4.4, Этап 3).
 */
class TransitionUseCasesTest {

    /** 1 -101- 2 -102- 3. */
    private val graph = FakeGraph()
        .edge(1, 101).edge(2, 101)
        .edge(2, 102).edge(3, 102)

    private val repositories = Triple(
        FakeActorRepository(graph),
        FakeMovieRepository(graph),
        FakeGameSessionRepository(),
    )

    private val startingSession = GameSession(
        sessionId = "transition-session",
        startActor = actor(1),
        targetActor = actor(3),
        currentActor = actor(1),
        chain = emptyList(),
        status = GameSessionStatus.IN_PROGRESS,
        startedAtMillis = 0L,
        updatedAtMillis = 0L,
        finishedAtMillis = null,
        stepCount = 0,
        shortestPath = null,
        generationSeed = null,
    )

    private fun validateUseCase() =
        ValidateTransitionUseCase(repositories.first, repositories.second) { Budget() }

    private fun commitUseCase() =
        CommitTransitionUseCase(repositories.third, repositories.first, repositories.second) { Budget() }

    private fun undoUseCase() = UndoLastTransitionUseCase(repositories.third)

    /** Сессия после шага 1 → 101 → 2. */
    private val afterFirstStep = startingSession.copy(
        chain = listOf(ChainStep(1, 1, 101, 2)),
        currentActor = actor(2),
        stepCount = 1,
    )

    // ValidateTransitionUseCase

    @Test
    fun `validate legal transition`() = runBlocking {
        val result = validateUseCase()(startingSession, movieId = 101, toActorId = 2)
        assertEquals(ValidationResult.Valid(1, 2), result.getOrNull())
    }

    @Test
    fun `validate actor not in cast`() = runBlocking {
        val result = validateUseCase()(startingSession, movieId = 101, toActorId = 3)
        val invalid = result.getOrNull() as? ValidationResult.Invalid
        assertTrue(invalid != null, "ожидался Invalid, получен $result")
        assertEquals(ChainInvalidReason.ACTOR_NOT_IN_CAST, invalid.reason)
    }

    @Test
    fun `validate movie repeat`() = runBlocking {
        val result = validateUseCase()(afterFirstStep, movieId = 101, toActorId = 3)
        val invalid = result.getOrNull() as? ValidationResult.Invalid
        assertTrue(invalid != null, "ожидался Invalid, получен $result")
        assertEquals(ChainInvalidReason.MOVIE_REPEAT, invalid.reason)
    }

    @Test
    fun `validate data failure propagates`() = runBlocking {
        repositories.second.failWith = AppError.Network
        val result = validateUseCase()(startingSession, movieId = 101, toActorId = 2)
        assertTrue(result is Result.Failure)
    }

    // CommitTransitionUseCase

    @Test
    fun `commit legal step updates session without win`() = runBlocking {
        val result = commitUseCase()(startingSession, movieId = 101, toActorId = 2)
        val outcome = result.getOrNull()
        assertTrue(outcome != null, "ожидался успех, получен $result")
        assertEquals(1, outcome.session.stepCount)
        assertEquals(2, outcome.session.currentActor.tmdbId)
        assertFalse(outcome.isWin)
        assertEquals(1, repositories.third.committedSteps.size)
    }

    @Test
    fun `commit step to target reports win`() = runBlocking {
        val result = commitUseCase()(afterFirstStep, movieId = 102, toActorId = 3)
        val outcome = result.getOrNull()
        assertTrue(outcome != null, "ожидался успех, получен $result")
        assertTrue(outcome.isWin)
        assertEquals(3, outcome.session.currentActor.tmdbId)
    }

    @Test
    fun `commit invalid step fails and commits nothing`() = runBlocking {
        val result = commitUseCase()(startingSession, movieId = 101, toActorId = 3)
        assertTrue(result is Result.Failure)
        assertEquals(0, repositories.third.committedSteps.size, "невалидный шаг не фиксируется")
    }

    @Test
    fun `commit repository failure propagates`() = runBlocking {
        repositories.third.commitFail = AppError.Server
        val result = commitUseCase()(startingSession, movieId = 101, toActorId = 2)
        assertTrue(result is Result.Failure)
        assertEquals(AppError.Server, (result as Result.Failure).error)
    }

    // UndoLastTransitionUseCase

    @Test
    fun `undo on empty chain returns session unchanged`() = runBlocking {
        val result = undoUseCase()(startingSession)
        assertEquals(startingSession, result.getOrNull())
        assertEquals(0, repositories.third.committedSteps.size)
    }

    @Test
    fun `undo restores previous actor and step count`() = runBlocking {
        val afterSecondStep = afterFirstStep.copy(
            chain = afterFirstStep.chain + ChainStep(2, 2, 102, 3),
            currentActor = actor(3),
            stepCount = 2,
        )
        val result = undoUseCase()(afterSecondStep)
        val session = result.getOrNull()
        assertTrue(session != null, "ожидался успех, получен $result")
        assertEquals(1, session.stepCount)
        assertEquals(2, session.currentActor.tmdbId, "назад к предыдущему актёру")
        assertEquals(1, session.chain.size)
    }

    @Test
    fun `undo repository failure propagates`() = runBlocking {
        repositories.third.undoFail = AppError.Server
        val result = undoUseCase()(afterFirstStep)
        assertTrue(result is Result.Failure)
        assertEquals(AppError.Server, (result as Result.Failure).error)
    }
}
