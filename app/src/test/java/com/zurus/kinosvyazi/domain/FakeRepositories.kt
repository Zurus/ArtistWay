package com.zurus.kinosvyazi.domain

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.model.GameResult
import com.zurus.kinosvyazi.domain.model.GameResultReason
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.model.GameSessionStatus
import com.zurus.kinosvyazi.domain.model.Movie
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.GamePairRepository
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.repository.PairGenerationError

/**
 * В-памяти граф «актёр ↔ фильм» для unit-тестов Этапа 3
 * (docs/05_roadmap.md: «fake-репозитории (in-memory граф)»).
 *
 * DSL: `FakeGraph().edge(1, 101).edge(2, 101)` — актёры 1 и 2 в фильме 101.
 */
class FakeGraph {

    private val moviesByActor = linkedMapOf<Int, MutableList<Int>>()
    private val castByMovie = linkedMapOf<Int, MutableList<Int>>()

    /** Добавляет симметричную рёберную связь актёр-фильм. */
    fun edge(actorId: Int, movieId: Int): FakeGraph = apply {
        moviesByActor.getOrPut(actorId) { mutableListOf() }.add(movieId)
        castByMovie.getOrPut(movieId) { mutableListOf() }.add(actorId)
    }

    fun actorMovieIds(actorId: Int): List<Int> = moviesByActor[actorId].orEmpty().distinct()

    fun movieCastIds(movieId: Int): List<Int> = castByMovie[movieId].orEmpty().distinct()
}

/**
 * [ActorRepository] поверх [FakeGraph].
 *
 * @param failWith задан — все чтения возвращают эту ошибку (тесты сетевых сбоев).
 */
class FakeActorRepository(
    private val graph: FakeGraph,
    var failWith: AppError? = null,
) : ActorRepository {

    var actorCalls = 0
        private set

    var movieCalls = 0
        private set

    override suspend fun getActor(actorId: Int): Result<Actor, AppError> {
        actorCalls++
        failWith?.let { return Result.failure(it) }
        return Result.Success(actor(actorId))
    }

    override suspend fun getActorMovies(actorId: Int): Result<List<Movie>, AppError> {
        movieCalls++
        failWith?.let { return Result.failure(it) }
        return Result.Success(
            graph.actorMovieIds(actorId).map { movieId -> Movie(movieId, "Фильм $movieId", null, null) },
        )
    }
}

/** [MovieRepository] поверх [FakeGraph]. */
class FakeMovieRepository(
    private val graph: FakeGraph,
    var failWith: AppError? = null,
) : MovieRepository {

    var movieCalls = 0
        private set

    var castCalls = 0
        private set

    override suspend fun getMovie(movieId: Int): Result<Movie, AppError> {
        movieCalls++
        failWith?.let { return Result.failure(it) }
        return Result.Success(Movie(movieId, "Фильм $movieId", null, null))
    }

    override suspend fun getMovieCast(movieId: Int): Result<List<Actor>, AppError> {
        castCalls++
        failWith?.let { return Result.failure(it) }
        return Result.Success(graph.movieCastIds(movieId).map { actorId -> actor(actorId) })
    }
}

/**
 * [GameSessionRepository] в памяти с семантикой реального репозитория:
 * последовательная нумерация шагов, откат к предыдущему актёру,
 * завершение партии.
 */
class FakeGameSessionRepository : GameSessionRepository {

    var startFail: AppError? = null
    var commitFail: AppError? = null
    var undoFail: AppError? = null
    var finishFail: AppError? = null

    val startedPairs = mutableListOf<GamePair>()
    val committedSteps = mutableListOf<ChainStep>()
    val finished = mutableListOf<GameResult>()
    var activeSession: GameSession? = null

    /** Все сохранённые сессии по [GameSession.sessionId] (аналог Room). */
    val sessions = mutableMapOf<String, GameSession>()

    override suspend fun startSession(pair: GamePair): Result<GameSession, AppError> {
        startFail?.let { return Result.failure(it) }
        startedPairs += pair
        val session = GameSession(
            sessionId = "session-${startedPairs.size}",
            startActor = pair.startActor,
            targetActor = pair.targetActor,
            currentActor = pair.startActor,
            chain = emptyList(),
            status = GameSessionStatus.IN_PROGRESS,
            startedAtMillis = 1000L,
            updatedAtMillis = 1000L,
            finishedAtMillis = null,
            stepCount = 0,
            shortestPath = pair.shortestPath,
            generationSeed = pair.seed,
        )
        activeSession = session
        sessions[session.sessionId] = session
        return Result.Success(session)
    }

    override suspend fun commitStep(session: GameSession, step: ChainStep): Result<GameSession, AppError> {
        commitFail?.let { return Result.failure(it) }
        if (step.stepIndex != session.stepCount + 1) {
            return Result.failure(AppError.DataInvalid("Нарушена нумерация шагов"))
        }
        val updated = session.copy(
            chain = session.chain + step,
            currentActor = actor(step.toActorId),
            updatedAtMillis = 2000L,
            stepCount = session.stepCount + 1,
        )
        committedSteps += step
        activeSession = updated
        sessions[updated.sessionId] = updated
        return Result.Success(updated)
    }

    override suspend fun undoLastStep(session: GameSession): Result<GameSession, AppError> {
        undoFail?.let { return Result.failure(it) }
        val steps = session.chain
        if (steps.isEmpty()) {
            return Result.failure(AppError.DataInvalid("Нет шагов для отмены"))
        }
        val previousActorId = if (steps.size == 1) {
            session.startActor.tmdbId
        } else {
            steps[steps.size - 2].toActorId
        }
        val updated = session.copy(
            chain = steps.dropLast(1),
            currentActor = actor(previousActorId),
            updatedAtMillis = 3000L,
            stepCount = session.stepCount - 1,
        )
        activeSession = updated
        sessions[updated.sessionId] = updated
        return Result.Success(updated)
    }

    override suspend fun finishSession(session: GameSession, result: GameResult): Result<GameSession, AppError> {
        finishFail?.let { return Result.failure(it) }
        finished += result
        val updated = session.copy(
            status = when (result.reason) {
                GameResultReason.WIN -> GameSessionStatus.WON
                GameResultReason.SURRENDER -> GameSessionStatus.SURRENDERED
                GameResultReason.DEAD_END -> GameSessionStatus.DEAD_END
                GameResultReason.ERROR -> GameSessionStatus.ERROR
                GameResultReason.NEW_GAME -> GameSessionStatus.SURRENDERED
            },
            updatedAtMillis = 4000L,
            finishedAtMillis = result.finishedAtMillis,
        )
        activeSession = updated
        sessions[updated.sessionId] = updated
        return Result.Success(updated)
    }

    override suspend fun restoreActiveSession(): GameSession? = activeSession

    override suspend fun findSession(sessionId: String): GameSession? = sessions[sessionId]
}

/** [GamePairRepository] с предопределённым результатом (тесты UseCase). */
class FakeGamePairRepository(
    var outcome: Result<GamePair, PairGenerationError> = Result.failure(PairGenerationError.NoValidPair),
) : GamePairRepository {

    var lastSeed: Long? = Long.MIN_VALUE

    override suspend fun generatePair(seed: Long?): Result<GamePair, PairGenerationError> {
        lastSeed = seed
        return outcome
    }
}

/** Фабрика тестовых [Actor]-объектов. */
fun actor(id: Int): Actor = Actor(id, "Актёр $id", null)

/**
 * Тестовая [GameSession] со стандартным стартом (1) и целью (3)
 * и заданной цепочкой; [currentActorId] — текущий актёр.
 */
fun sessionWith(chain: List<ChainStep>, currentActorId: Int, sessionId: String = "test-session"): GameSession =
    GameSession(
        sessionId = sessionId,
        startActor = actor(1),
        targetActor = actor(3),
        currentActor = actor(currentActorId),
        chain = chain,
        status = GameSessionStatus.IN_PROGRESS,
        startedAtMillis = 0L,
        updatedAtMillis = 0L,
        finishedAtMillis = null,
        stepCount = chain.size,
        shortestPath = null,
        generationSeed = null,
    )

/** Сессия с произвольными актёрами (для тестов Finish/DeadEnd). */
fun sessionBetween(startId: Int, targetId: Int, chain: List<ChainStep>): GameSession =
    GameSession(
        sessionId = "between-${startId}-${targetId}",
        startActor = actor(startId),
        targetActor = actor(targetId),
        currentActor = actor(chain.lastOrNull()?.toActorId ?: startId),
        chain = chain,
        status = GameSessionStatus.IN_PROGRESS,
        startedAtMillis = 0L,
        updatedAtMillis = 0L,
        finishedAtMillis = null,
        stepCount = chain.size,
        shortestPath = chain,
        generationSeed = 7L,
    )
