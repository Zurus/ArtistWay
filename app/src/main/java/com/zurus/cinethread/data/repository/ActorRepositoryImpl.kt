package com.zurus.cinethread.data.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.data.cache.CachePolicy
import com.zurus.cinethread.data.cache.InMemoryCache
import com.zurus.cinethread.data.local.dao.ActorDao
import com.zurus.cinethread.data.local.dao.ActorMovieCreditDao
import com.zurus.cinethread.data.local.dao.MovieDao
import com.zurus.cinethread.data.local.entity.ActorEntity
import com.zurus.cinethread.data.local.entity.ActorMovieCreditEntity
import com.zurus.cinethread.data.local.entity.MovieEntity
import com.zurus.cinethread.data.mapper.MovieMapper
import com.zurus.cinethread.data.mapper.PersonMapper
import com.zurus.cinethread.data.mapper.PersonMovieCreditMapper
import com.zurus.cinethread.data.remote.TmdbConfigurationProvider
import com.zurus.cinethread.data.remote.TmdbErrorMapper
import com.zurus.cinethread.data.remote.api.TmdbPersonApi
import com.zurus.cinethread.data.remote.dto.TmdbPersonMovieCreditsResponseDto
import com.zurus.cinethread.domain.model.Actor
import com.zurus.cinethread.domain.model.Movie
import com.zurus.cinethread.domain.repository.ActorRepository
import com.zurus.cinethread.data.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

/**
 * Реализация [ActorRepository] (docs/03_data_layer.md §5, «Порядок чтения»):
 * in-memory → Room (TTL) → TMDB → write-through в Room.
 *
 * - Свежий Room-кэш не вызывает TMDB.
 * - При сетевой ошибке возвращается устаревший Room-кэш, если он есть;
 *   иначе — классифицированная ошибка ([TmdbErrorMapper]).
 * - Записи с `id = null` не сохраняются и не возвращаются.
 */
@Singleton
class ActorRepositoryImpl @Inject constructor(
    private val personApi: TmdbPersonApi,
    private val configurationProvider: TmdbConfigurationProvider,
    private val actorDao: ActorDao,
    private val movieDao: MovieDao,
    private val actorMovieCreditDao: ActorMovieCreditDao,
    private val personMapper: PersonMapper,
    private val movieMapper: MovieMapper,
    private val personMovieCreditMapper: PersonMovieCreditMapper,
    private val clock: Clock,
) : ActorRepository {

    private val actorCache = InMemoryCache<Actor>(CachePolicy.IN_MEMORY_LIST_TTL.toMillis(), clock::nowMillis)
    private val filmographyCache = InMemoryCache<List<Movie>>(
        CachePolicy.IN_MEMORY_LIST_TTL.toMillis(),
        clock::nowMillis,
    )

    override suspend fun getActor(actorId: Int): Result<Actor, AppError> {
        actorCache.get(actorId)?.let { return Result.success(it) }

        val stored = actorDao.getById(actorId)
        val fresh = stored?.takeIf {
            CachePolicy.isFresh(it.fetchedAtMillis, CachePolicy.ACTOR_TTL, clock.nowMillis())
        }
        return if (fresh != null) {
            val actor = personMapper.toDomain(fresh, imageBaseUrl())
            actorCache.put(actorId, actor)
            Result.success(actor)
        } else {
            fetchAndCacheActor(actorId, stored)
        }
    }

    override suspend fun getActorMovies(personId: Int): Result<List<Movie>, AppError> {
        filmographyCache.get(personId)?.let { return Result.success(it) }

        val storedCredits = actorMovieCreditDao.getByPersonId(personId)
        return if (storedCredits.isNotEmpty() && storedCredits.isFresh()) {
            val movies = moviesFromStoredCredits(storedCredits)
            filmographyCache.put(personId, movies)
            Result.success(movies)
        } else {
            fetchAndCacheFilmography(personId, storedCredits)
        }
    }

    // --- Запросы TMDB с write-through ---

    private suspend fun fetchAndCacheActor(actorId: Int, stored: ActorEntity?): Result<Actor, AppError> {
        return try {
            val dto = personApi.getPerson(actorId)
            val entity = personMapper.toEntity(dto, clock.nowMillis())
            if (entity == null) {
                Timber.w("TMDB /person/%d вернул null id", actorId)
                Result.failure(AppError.DataInvalid("TMDB person id is null"))
            } else {
                actorDao.upsert(entity)
                val actor = personMapper.toDomain(entity, imageBaseUrl())
                actorCache.put(actorId, actor)
                Result.success(actor)
            }
        } catch (e: HttpException) {
            staleActorFallback(actorId, stored, e)
        } catch (e: IOException) {
            staleActorFallback(actorId, stored, e)
        } catch (e: SerializationException) {
            staleActorFallback(actorId, stored, e)
        }
    }

    private suspend fun fetchAndCacheFilmography(
        personId: Int,
        storedCredits: List<ActorMovieCreditEntity>,
    ): Result<List<Movie>, AppError> {
        return try {
            val response = personApi.getPersonMovieCredits(personId)
            buildAndSaveFilmography(personId, response)
        } catch (e: HttpException) {
            staleFilmographyFallback(personId, storedCredits, e)
        } catch (e: IOException) {
            staleFilmographyFallback(personId, storedCredits, e)
        } catch (e: SerializationException) {
            staleFilmographyFallback(personId, storedCredits, e)
        }
    }

    private suspend fun buildAndSaveFilmography(
        personId: Int,
        response: TmdbPersonMovieCreditsResponseDto,
    ): Result<List<Movie>, AppError> {
        val now = clock.nowMillis()
        val movieEntities = linkedMapOf<Int, MovieEntity>()
        val creditEntities = ArrayList<ActorMovieCreditEntity>()
        for (creditDto in response.cast.orEmpty()) {
            val movieEntity = personMovieCreditMapper.toMovieEntity(creditDto, now)
            if (movieEntity == null) {
                Timber.w("Кредит без movie_id для person %d, пропускаю", personId)
            } else {
                movieEntities[movieEntity.movieId] = movieEntity
                personMovieCreditMapper.toCreditEntity(creditDto, personId, now)?.let { creditEntities += it }
            }
        }

        if (movieEntities.isNotEmpty()) {
            movieDao.upsertAll(movieEntities.values.toList())
            actorMovieCreditDao.upsertAll(creditEntities)
            actorMovieCreditDao.deleteStaleLinks(personId, movieEntities.keys.toList())
        } else {
            // Пустой cast — валидное состояние (актёр без фильмов): очищаем старые связи.
            actorMovieCreditDao.deleteByPersonId(personId)
        }

        val movies = movieEntities.values.toList()
            .map { movieMapper.toDomain(it, imageBaseUrl()) }
            .sortedWith(filmographyComparator)
        filmographyCache.put(personId, movies)
        return Result.success(movies)
    }

    // --- Вспомогательные функции ---

    private suspend fun imageBaseUrl(): String = configurationProvider.getImageBaseUrl()

    private fun List<ActorMovieCreditEntity>.isFresh(): Boolean {
        val newest = maxOf { it.fetchedAtMillis }
        return CachePolicy.isFresh(newest, CachePolicy.ACTOR_MOVIE_CREDITS_TTL, clock.nowMillis())
    }

    private suspend fun moviesFromStoredCredits(credits: List<ActorMovieCreditEntity>): List<Movie> {
        val movieEntities = movieDao.getByIds(credits.map { it.movieId })
        val byId = movieEntities.associateBy { it.movieId }
        val imageBase = imageBaseUrl()
        return credits.mapNotNull { credit -> byId[credit.movieId] }
            .distinctBy { it.movieId }
            .map { movieMapper.toDomain(it, imageBase) }
            .sortedWith(filmographyComparator)
    }

    private suspend fun staleActorFallback(
        actorId: Int,
        stored: ActorEntity?,
        throwable: Throwable,
    ): Result<Actor, AppError> {
        if (stored != null) {
            Timber.w(throwable, "TMDB недоступен для person %d, использую устаревший Room-кэш", actorId)
            val actor = personMapper.toDomain(stored, imageBaseUrl())
            actorCache.put(actorId, actor)
            return Result.success(actor)
        }
        return Result.failure(TmdbErrorMapper.toAppError(throwable))
    }

    private suspend fun staleFilmographyFallback(
        personId: Int,
        storedCredits: List<ActorMovieCreditEntity>,
        throwable: Throwable,
    ): Result<List<Movie>, AppError> {
        if (storedCredits.isNotEmpty()) {
            Timber.w(throwable, "TMDB недоступен для filmography %d, использую устаревший Room-кэш", personId)
            val movies = moviesFromStoredCredits(storedCredits)
            filmographyCache.put(personId, movies)
            return Result.success(movies)
        }
        return Result.failure(TmdbErrorMapper.toAppError(throwable))
    }

    private companion object {
        /** Фильмография: сначала новые годы, безгодовые — в конце, затем по названию. */
        val filmographyComparator = compareByDescending<Movie> { it.year?.value ?: 0 }
            .thenBy { it.title }
    }
}
