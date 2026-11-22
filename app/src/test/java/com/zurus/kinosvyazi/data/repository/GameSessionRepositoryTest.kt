package com.zurus.kinosvyazi.data.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.data.local.entity.ChainStepEntity
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.model.GameResult
import com.zurus.kinosvyazi.domain.model.GameResultReason
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.model.GameSessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit-тесты `GameSessionRepositoryImpl` (docs/05_roadmap.md, Этап 2):
 * сохранение/восстановление активной партии, сортировка шагов по
 * `stepIndex`, commit/undo, завершение.
 */
@RunWith(RobolectricTestRunner::class)
class GameSessionRepositoryTest : TmdbRepositoryTest() {

    @Test
    fun startSession_savesSessionActorsAndReturnsDomain() = runBlocking {
        val result = gameSessionRepository().startSession(pair())

        val session = (result as Result.Success).value
        assertEquals(GameSessionStatus.IN_PROGRESS, session.status)
        assertEquals(0, session.stepCount)
        assertEquals(START_ID, session.currentActor.tmdbId)
        assertEquals(pair().shortestPath, session.shortestPath)

        val entity = gameSessionDao.getActive(GameSessionStatus.IN_PROGRESS.name)
        assertNotNull("Партия сохранена в Room", entity)
        assertEquals(START_ID, entity!!.startPersonId)
        assertEquals(TARGET_ID, entity.targetPersonId)
        assertEquals(42L, entity.generationSeed)
        assertNotNull(entity.shortestPathJson)
        assertNotNull(actorDao.getById(START_ID))
        assertNotNull(actorDao.getById(TARGET_ID))
    }

    @Test
    fun startSession_keepsProfilePathFromPhotoUrl() = runBlocking {
        gameSessionRepository().startSession(
            GamePair(
                startActor = Actor(START_ID, "Start", "${imageBaseUrl}w185/start.jpg"),
                targetActor = actor(TARGET_ID, "Target"),
                shortestPath = emptyList(),
                seed = null,
            )
        )

        assertEquals("/start.jpg", actorDao.getById(START_ID)?.profilePath)
    }

    @Test
    fun commitStep_updatesCurrentActorAndStepCount() = runBlocking {
        val repository = gameSessionRepository()
        val session = repository.startSession(pair()).getOrThrow()

        val afterFirst = repository.commitStep(session, step(1, START_ID, 11, MID_ID)).getOrThrow()
        val afterSecond = repository.commitStep(afterFirst, step(2, MID_ID, 12, TARGET_ID)).getOrThrow()

        assertEquals(1, afterFirst.stepCount)
        assertEquals(MID_ID, afterFirst.currentActor.tmdbId)
        assertEquals(2, afterSecond.stepCount)
        assertEquals(TARGET_ID, afterSecond.currentActor.tmdbId)
        assertEquals(2, gameSessionDao.getSteps(activeSessionId()).size)
    }

    @Test
    fun commitStep_wrongStepIndex_rejected() = runBlocking {
        val repository = gameSessionRepository()
        val session = repository.startSession(pair()).getOrThrow()

        val result = repository.commitStep(session, step(2, START_ID, 11, MID_ID))

        assertEquals(AppError.DataInvalid::class, (result as Result.Failure).error::class)
        assertEquals(0, gameSessionDao.getSteps(activeSessionId()).size)
    }

    @Test
    fun undoLastStep_revertsCurrentActorAndStepCount() = runBlocking {
        val repository = gameSessionRepository()
        val session = repository.startSession(pair()).getOrThrow()
        repository.commitStep(session, step(1, START_ID, 11, MID_ID)).getOrThrow()
        repository.commitStep(session, step(2, MID_ID, 12, TARGET_ID)).getOrThrow()

        val afterUndo = repository.undoLastStep(session).getOrThrow()

        assertEquals(1, afterUndo.stepCount)
        assertEquals(MID_ID, afterUndo.currentActor.tmdbId)
        assertEquals(1, gameSessionDao.getSteps(activeSessionId()).size)
    }

    @Test
    fun undoLastStep_emptyChain_rejected() = runBlocking {
        val repository = gameSessionRepository()
        val session = repository.startSession(pair()).getOrThrow()

        val result = repository.undoLastStep(session)

        assertEquals(AppError.DataInvalid::class, (result as Result.Failure).error::class)
    }

    @Test
    fun finishSession_won_setsStatusAndFinishedAt() = runBlocking {
        val repository = gameSessionRepository()
        val session = repository.startSession(pair()).getOrThrow()
        repository.commitStep(session, step(1, START_ID, 11, MID_ID)).getOrThrow()
        val result = gameResult(session, GameResultReason.WIN)

        val finished = repository.finishSession(session, result).getOrThrow()

        assertEquals(GameSessionStatus.WON, finished.status)
        assertEquals(result.finishedAtMillis, finished.finishedAtMillis)
        assertNull("Завершённая партия больше не активна", repository.restoreActiveSession())
    }

    @Test
    fun finishSession_newGame_mapsToSurrendered() = runBlocking {
        val repository = gameSessionRepository()
        val session = repository.startSession(pair()).getOrThrow()
        val result = gameResult(session, GameResultReason.NEW_GAME)

        val finished = repository.finishSession(session, result).getOrThrow()

        assertEquals(GameSessionStatus.SURRENDERED, finished.status)
    }

    @Test
    fun restoreActiveSession_returnsSessionWithStepsSortedByIndex() = runBlocking {
        val repository = gameSessionRepository()
        repository.startSession(pair()).getOrThrow()
        // Шаги записаны в Room вразнобой — восстановление должно отсортировать.
        val entity = gameSessionDao.getActive(GameSessionStatus.IN_PROGRESS.name)!!
        gameSessionDao.upsertSteps(
            listOf(
                stepEntity(entity.sessionId, 2, MID_ID, 12, TARGET_ID),
                stepEntity(entity.sessionId, 1, START_ID, 11, MID_ID),
            )
        )
        gameSessionDao.upsert(entity.copy(stepCount = 2, currentPersonId = TARGET_ID))

        val restored = repository.restoreActiveSession()

        assertNotNull("Активная партия восстановлена", restored)
        assertEquals(listOf(1, 2), restored!!.chain.map { it.stepIndex })
        assertEquals(TARGET_ID, restored.currentActor.tmdbId)
        assertEquals(2, restored.stepCount)
    }

    @Test
    fun restoreActiveSession_noActiveSession_null() = runBlocking {
        assertNull(gameSessionRepository().restoreActiveSession())
    }

    @Test
    fun restoreActiveSession_afterFinish_null() = runBlocking {
        val repository = gameSessionRepository()
        val session = repository.startSession(pair()).getOrThrow()
        repository.finishSession(session, gameResult(session, GameResultReason.SURRENDER)).getOrThrow()

        assertNull(repository.restoreActiveSession())
    }

    // --- Фикстуры ---

    private fun actor(id: Int, name: String) = Actor(id, name, null)

    private fun pair(): GamePair =
        GamePair(
            startActor = actor(START_ID, "Start"),
            targetActor = actor(TARGET_ID, "Target"),
            shortestPath = listOf(step(1, START_ID, 11, MID_ID), step(2, MID_ID, 12, TARGET_ID)),
            seed = 42L,
        )

    private fun step(index: Int, fromId: Int, movieId: Int, toId: Int) =
        ChainStep(stepIndex = index, fromActorId = fromId, movieId = movieId, toActorId = toId)

    private fun stepEntity(sessionId: String, index: Int, fromId: Int, movieId: Int, toId: Int) =
        ChainStepEntity(
            sessionId = sessionId,
            stepIndex = index,
            fromPersonId = fromId,
            movieId = movieId,
            toPersonId = toId,
            createdAtMillis = currentNow,
        )

    private fun gameResult(session: GameSession, reason: GameResultReason): GameResult =
        GameResult(
            reason = reason,
            startActor = session.startActor,
            targetActor = session.targetActor,
            lastActor = session.currentActor,
            chain = session.chain,
            stepCount = session.stepCount,
            guaranteedDistance = 2,
            startedAtMillis = session.startedAtMillis,
            finishedAtMillis = currentNow + 60_000,
        )

    private suspend fun activeSessionId(): String =
        gameSessionDao.getActive(GameSessionStatus.IN_PROGRESS.name)!!.sessionId

    private fun <T> Result<T, AppError>.getOrThrow(): T {
        val error = errorOrNull()
        return if (error == null) {
            getOrNull()!!
        } else {
            throw AssertionError("Ожидался успех, получена ошибка: $error")
        }
    }

    private companion object {
        const val START_ID = 1
        const val MID_ID = 2
        const val TARGET_ID = 3
    }
}
