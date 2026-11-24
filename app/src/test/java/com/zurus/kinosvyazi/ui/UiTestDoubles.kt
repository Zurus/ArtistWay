package com.zurus.kinosvyazi.ui

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.FakeGraph
import com.zurus.kinosvyazi.domain.FakeGameSessionRepository
import com.zurus.kinosvyazi.domain.actor
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.model.GameResult
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.model.Movie
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.DataAvailabilityChecker
import com.zurus.kinosvyazi.domain.repository.GamePairRepository
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.repository.PairGenerationError

/**
 * Настроечное состояние для Compose UI-тестов (docs/05_roadmap.md, Этап 6,
 * пункт 2: happy path до победы, отмена перехода, тупик, ошибка сети).
 *
 * Hilt-граф в UI-тестах подменяет [com.zurus.kinosvyazi.di.RepositoryModule]
 * делегирующими синглтонами ([UiTestRepositoriesModule]), которые читают
 * [current] на момент каждого вызова: [reset] между тестами создаёт
 * свежий in-memory граф и хранилище, не трогая уже собранный граф Hilt.
 */
object UiTestDoubles {

    /** Состояние одного UI-теста: граф «актёр ↔ фильм» и результат генератора. */
    class State {
        // var: тесты перестраивают граф под конкретный сценарий.
        var graph = FakeGraph()
        val sessionRepository = FakeGameSessionRepository()

        /** Пара, которую «генератор» выдаст для новой партии (или [pairError]). */
        var pair: GamePair? = null

        var pairError: PairGenerationError? = null

        /** Все чтения репозитория актёров возвращают эту ошибку (тесты офлайна). */
        var actorFail: AppError? = null

        /** Все чтения репозитория фильмов возвращают эту ошибку. */
        var movieFail: AppError? = null

        var tmdbAvailable: Boolean = true
    }

    @Volatile
    var current = State()

    fun reset() {
        current = State()
    }

    /**
     * Делегирующий [ActorRepository]: стабильный синглтон для Hilt-графа,
     * данные — из [current].
     */
    class ActorRepo : ActorRepository {
        override suspend fun getActor(actorId: Int): Result<Actor, AppError> {
            val state = current
            state.actorFail?.let { return Result.failure(it) }
            return Result.Success(actor(actorId))
        }

        override suspend fun getActorMovies(actorId: Int): Result<List<Movie>, AppError> {
            val state = current
            state.actorFail?.let { return Result.failure(it) }
            val movies = state.graph.actorMovieIds(actorId).map { id -> Movie(id, "Фильм $id", null, null) }
            return Result.Success(movies)
        }
    }

    /** Делегирующий [MovieRepository]: стабильный синглтон для Hilt-графа. */
    class MovieRepo : MovieRepository {
        override suspend fun getMovie(movieId: Int): Result<Movie, AppError> {
            val state = current
            state.movieFail?.let { return Result.failure(it) }
            return Result.Success(Movie(movieId, "Фильм $movieId", null, null))
        }

        override suspend fun getMovieCast(movieId: Int): Result<List<Actor>, AppError> {
            val state = current
            state.movieFail?.let { return Result.failure(it) }
            return Result.Success(state.graph.movieCastIds(movieId).map { id -> actor(id) })
        }
    }

    /** Делегирующий [GamePairRepository]: предопределённая пара (детерминизм UI-тестов). */
    class PairRepo : GamePairRepository {
        override suspend fun generatePair(seed: Long?): Result<GamePair, PairGenerationError> {
            val state = current
            val failure = state.pairError
            val pair = state.pair
            return when {
                failure != null -> Result.failure(failure)
                pair != null -> Result.Success(pair)
                else -> Result.failure(PairGenerationError.NoValidPair)
            }
        }
    }

    /** Делегирующий [DataAvailabilityChecker]. */
    class AvailabilityChecker : DataAvailabilityChecker {
        override suspend fun check(): Result<Unit, AppError> =
            if (current.tmdbAvailable) {
                Result.Success(Unit)
            } else {
                Result.failure(AppError.Offline)
            }
    }

    /**
     * Делегирующий [GameSessionRepository] (в памяти, без Room).
     * Делегирование динамическое: [reset] подменяет хранилище между тестами,
     * а Hilt-синглтон остаётся тем же объектом.
     */
    class SessionRepo : GameSessionRepository {
        private val delegate get() = current.sessionRepository

        override suspend fun startSession(pair: GamePair): Result<GameSession, AppError> =
            delegate.startSession(pair)

        override suspend fun commitStep(
            session: GameSession,
            step: ChainStep,
        ): Result<GameSession, AppError> = delegate.commitStep(session, step)

        override suspend fun undoLastStep(session: GameSession): Result<GameSession, AppError> =
            delegate.undoLastStep(session)

        override suspend fun finishSession(
            session: GameSession,
            result: GameResult,
        ): Result<GameSession, AppError> = delegate.finishSession(session, result)

        override suspend fun restoreActiveSession(): GameSession? =
            delegate.restoreActiveSession()

        override suspend fun findSession(sessionId: String): GameSession? =
            delegate.findSession(sessionId)
    }
}
