package com.zurus.kinosvyazi.data.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.data.local.dao.ActorDao
import com.zurus.kinosvyazi.data.local.dao.GameSessionDao
import com.zurus.kinosvyazi.data.local.entity.ActorEntity
import com.zurus.kinosvyazi.data.local.entity.ChainStepEntity
import com.zurus.kinosvyazi.data.local.entity.GameSessionEntity
import com.zurus.kinosvyazi.data.mapper.GameSessionMapper
import com.zurus.kinosvyazi.data.mapper.PersonMapper
import com.zurus.kinosvyazi.data.remote.TmdbConfigurationProvider
import com.zurus.kinosvyazi.data.remote.TmdbImageUrls
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.model.GameResult
import com.zurus.kinosvyazi.domain.model.GameResultReason
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.model.GameSessionStatus
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.data.time.Clock
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import java.util.UUID

/**
 * Реализация [GameSessionRepository] (docs/03_data_layer.md §2, §8):
 * партия и цепочка шагов в Room, восстановление после перезапуска
 * приложения, Undo последнего хода (docs/01_concept.md §6.16).
 *
 * Доменная [GameSession] не несёт `sessionId`, поэтому активная партия
 * идентифицируется по совпадению (стартовый актёр, целевой актёр,
 * время начала) с единственной партией статуса `IN_PROGRESS`.
 */
@Singleton
class GameSessionRepositoryImpl @Inject constructor(
    private val gameSessionDao: GameSessionDao,
    private val actorDao: ActorDao,
    private val configurationProvider: TmdbConfigurationProvider,
    private val gameSessionMapper: GameSessionMapper,
    private val clock: Clock,
) : GameSessionRepository {

    override suspend fun startSession(pair: GamePair): Result<GameSession, AppError> = runInRoom {
        val now = clock.nowMillis()
        val sessionId = newSessionId()
        actorDao.upsertAll(
            listOf(
                pair.startActor.toEntity(now),
                pair.targetActor.toEntity(now),
            )
        )
        val session = GameSession(
            startActor = pair.startActor,
            targetActor = pair.targetActor,
            currentActor = pair.startActor,
            chain = emptyList(),
            status = GameSessionStatus.IN_PROGRESS,
            startedAtMillis = now,
            updatedAtMillis = now,
            finishedAtMillis = null,
            stepCount = 0,
            shortestPath = pair.shortestPath,
            generationSeed = pair.seed,
        )
        gameSessionDao.upsert(gameSessionMapper.toEntity(session, sessionId, now))
        Result.success(session)
    }

    override suspend fun commitStep(session: GameSession, step: ChainStep): Result<GameSession, AppError> =
        runInRoom {
            val entity = activeSessionEntityFor(session)
                ?: return@runInRoom Result.failure(
                    AppError.DataInvalid("Активная партия не найдена в Room"),
                )
            val expectedIndex = entity.stepCount + 1
            if (step.stepIndex != expectedIndex) {
                Timber.w("Коммит шага %d, ожидался %d (партия %s)", step.stepIndex, expectedIndex, entity.sessionId)
                return@runInRoom Result.failure(
                    AppError.DataInvalid("Ожидался шаг $expectedIndex, получен ${step.stepIndex}"),
                )
            }
            val now = clock.nowMillis()
            gameSessionDao.upsertSteps(
                listOf(
                    ChainStepEntity(
                        sessionId = entity.sessionId,
                        stepIndex = step.stepIndex,
                        fromPersonId = step.fromActorId,
                        movieId = step.movieId,
                        toPersonId = step.toActorId,
                        createdAtMillis = now,
                    )
                )
            )
            val updated = entity.copy(
                currentPersonId = step.toActorId,
                stepCount = expectedIndex,
                updatedAtMillis = now,
            )
            gameSessionDao.upsert(updated)
            Result.success(loadDomainSession(updated))
        }

    override suspend fun undoLastStep(session: GameSession): Result<GameSession, AppError> = runInRoom {
        val entity = activeSessionEntityFor(session)
            ?: return@runInRoom Result.failure(AppError.DataInvalid("Активная партия не найдена в Room"))
        if (entity.stepCount <= 0) {
            return@runInRoom Result.failure(AppError.DataInvalid("Цепочка пуста, отменять нечего"))
        }
        val lastStep = gameSessionDao.getSteps(entity.sessionId).lastOrNull()
            ?: return@runInRoom Result.failure(AppError.DataInvalid("Последний шаг партии не найден"))
        gameSessionDao.deleteStep(entity.sessionId, lastStep.stepIndex)
        val updated = entity.copy(
            currentPersonId = lastStep.fromPersonId,
            stepCount = entity.stepCount - 1,
            updatedAtMillis = clock.nowMillis(),
        )
        gameSessionDao.upsert(updated)
        Result.success(loadDomainSession(updated))
    }

    override suspend fun finishSession(
        session: GameSession,
        result: GameResult,
    ): Result<GameSession, AppError> = runInRoom {
        val entity = activeSessionEntityFor(session)
            ?: return@runInRoom Result.failure(AppError.DataInvalid("Активная партия не найдена в Room"))
        val updated = entity.copy(
            status = result.reason.toSessionStatus().name,
            finishedAtMillis = result.finishedAtMillis,
            updatedAtMillis = clock.nowMillis(),
        )
        gameSessionDao.upsert(updated)
        Result.success(loadDomainSession(updated))
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun restoreActiveSession(): GameSession? {
        val entity = try {
            gameSessionDao.getActive(GameSessionStatus.IN_PROGRESS.name)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Timber.e(e, "Не удалось прочитать активную партию, восстанавливать нечего")
            return null
        }
        if (entity == null) {
            return null
        }
        return try {
            loadDomainSession(entity)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Timber.e(e, "Не удалось восстановить активную партию %s", entity.sessionId)
            null
        }
    }

    // --- Вспомогательные функции ---

    private suspend fun activeSessionEntityFor(session: GameSession): GameSessionEntity? {
        val entity = gameSessionDao.getActive(GameSessionStatus.IN_PROGRESS.name) ?: return null
        val matches = entity.startPersonId == session.startActor.tmdbId &&
            entity.targetPersonId == session.targetActor.tmdbId &&
            entity.startedAtMillis == session.startedAtMillis
        return if (matches) {
            entity
        } else {
            Timber.w("Активная партия в Room не совпадает с переданной сессией")
            null
        }
    }

    private suspend fun loadDomainSession(entity: GameSessionEntity): GameSession {
        val steps = gameSessionDao.getSteps(entity.sessionId)
        return gameSessionMapper.toDomain(
            sessionEntity = entity,
            startActor = actorOrPlaceholder(entity.startPersonId),
            targetActor = actorOrPlaceholder(entity.targetPersonId),
            currentActor = actorOrPlaceholder(entity.currentPersonId),
            steps = steps,
            imageBaseUrl = configurationProvider.getImageBaseUrl(),
        )
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    private suspend fun actorOrPlaceholder(personId: Int): ActorEntity =
        actorDao.getById(personId) ?: run {
            Timber.w("Актёр %d отсутствует в кэше, использую заглушку", personId)
            ActorEntity(
                personId = personId,
                name = PersonMapper.UNKNOWN_NAME,
                profilePath = null,
                knownForDepartment = null,
                popularity = null,
                fetchedAtMillis = clock.nowMillis(),
            )
        }

    /**
     * Ошибки Room переводятся в `DataInvalid`.
     * Намеренно ловит широкий `Exception`: Room может бросать разные
     * SQLite-исключения в зависимости от версии драйвера; отмена
     * корутины пробрасывается наружу.
     */
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> runInRoom(block: () -> Result<T, AppError>): Result<T, AppError> {
        return try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Timber.e(e, "Room: ошибка базы данных")
            Result.failure(AppError.DataInvalid("Ошибка базы данных"))
        }
    }

    private fun GameResultReason.toSessionStatus(): GameSessionStatus = when (this) {
        GameResultReason.WIN -> GameSessionStatus.WON
        GameResultReason.SURRENDER -> GameSessionStatus.SURRENDERED
        GameResultReason.DEAD_END -> GameSessionStatus.DEAD_END
        GameResultReason.ERROR -> GameSessionStatus.ERROR
        // Игрок бросил партию, начав новую — ближайший статус: «сдался».
        GameResultReason.NEW_GAME -> GameSessionStatus.SURRENDERED
    }

    private fun Actor.toEntity(fetchedAtMillis: Long): ActorEntity =
        ActorEntity(
            personId = tmdbId,
            name = name.ifBlank { PersonMapper.UNKNOWN_NAME },
            // Доменная модель несёт готовый URL; относительный путь извлекаем
            // обратно, чтобы Room продолжал хранить `profilePath`
            // (симметрично `PersonMapper.toDomain` — без префикса размера).
            profilePath = photoUrl?.takeIf { it.contains(PROFILE_URL_MARKER) }
                ?.substringAfterLast(PROFILE_URL_MARKER),
            knownForDepartment = null,
            popularity = null,
            fetchedAtMillis = fetchedAtMillis,
        )

    private companion object {
        /** Маркер фото профиля: базовый URL TMDB + размер (путь идёт следом, с `/`). */
        const val PROFILE_URL_MARKER = "/t/p/${TmdbImageUrls.PROFILE_SIZE}"
    }
}
