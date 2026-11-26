package com.zurus.cinethread.data.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.data.cache.CachePolicy
import com.zurus.cinethread.data.cache.InMemoryCache
import com.zurus.cinethread.data.local.dao.ActorDao
import com.zurus.cinethread.data.local.dao.MovieCastDao
import com.zurus.cinethread.data.local.dao.MovieDao
import com.zurus.cinethread.data.local.entity.MovieCastEntity
import com.zurus.cinethread.data.local.entity.MovieEntity
import com.zurus.cinethread.data.mapper.MovieCastMapper
import com.zurus.cinethread.data.mapper.MovieMapper
import com.zurus.cinethread.data.mapper.PersonMapper
import com.zurus.cinethread.data.remote.TmdbConfigurationProvider
import com.zurus.cinethread.data.remote.TmdbErrorMapper
import com.zurus.cinethread.data.remote.TmdbImageUrls
import com.zurus.cinethread.data.remote.api.TmdbMovieApi
import com.zurus.cinethread.data.remote.dto.TmdbMovieCastMemberDto
import com.zurus.cinethread.data.remote.dto.TmdbMovieCreditsResponseDto
import com.zurus.cinethread.domain.model.Actor
import com.zurus.cinethread.domain.model.Movie
import com.zurus.cinethread.domain.repository.MovieRepository
import com.zurus.cinethread.data.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

/**
 * Реализация [MovieRepository] (docs/03_data_layer.md §5, «Порядок чтения»):
 * in-memory → Room (TTL) → TMDB → write-through в Room.
 *
 * Состав берётся только из `cast` (`crew` игнорируется,
 * docs/01_concept.md §6.2); пустой `cast` — валидный `Success(emptyList())`
 * (docs/01_concept.md §6.3–6.4). Записи с `id = null` не сохраняются.
 */
@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val movieApi: TmdbMovieApi,
    private val configurationProvider: TmdbConfigurationProvider,
    private val movieDao: MovieDao,
    private val movieCastDao: MovieCastDao,
    private val actorDao: ActorDao,
    private val movieMapper: MovieMapper,
    private val personMapper: PersonMapper,
    private val movieCastMapper: MovieCastMapper,
    private val clock: Clock,
) : MovieRepository {

    private val movieCache = InMemoryCache<Movie>(CachePolicy.IN_MEMORY_LIST_TTL.toMillis(), clock::nowMillis)
    private val castCache = InMemoryCache<List<Actor>>(CachePolicy.IN_MEMORY_LIST_TTL.toMillis(), clock::nowMillis)

    override suspend fun getMovie(movieId: Int): Result<Movie, AppError> {
        movieCache.get(movieId)?.let { return Result.success(it) }

        val stored = movieDao.getById(movieId)
        val fresh = stored?.takeIf {
            CachePolicy.isFresh(it.fetchedAtMillis, CachePolicy.MOVIE_TTL, clock.nowMillis())
        }
        return if (fresh != null) {
            val movie = movieMapper.toDomain(fresh, imageBaseUrl())
            movieCache.put(movieId, movie)
            Result.success(movie)
        } else {
            fetchAndCacheMovie(movieId, stored)
        }
    }

    override suspend fun getMovieCast(movieId: Int): Result<List<Actor>, AppError> {
        castCache.get(movieId)?.let { return Result.success(it) }

        val storedCast = movieCastDao.getByMovieId(movieId)
        return if (storedCast.isNotEmpty() && storedCast.isFresh()) {
            val actors = actorsFromStoredCast(storedCast)
            castCache.put(movieId, actors)
            Result.success(actors)
        } else {
            fetchAndCacheCast(movieId, storedCast)
        }
    }

    // --- Запросы TMDB с write-through ---

    private suspend fun fetchAndCacheMovie(movieId: Int, stored: MovieEntity?): Result<Movie, AppError> {
        return try {
            val dto = movieApi.getMovie(movieId)
            val entity = movieMapper.toEntity(dto, clock.nowMillis())
            if (entity == null) {
                Timber.w("TMDB /movie/%d вернул null id", movieId)
                Result.failure(AppError.DataInvalid("TMDB movie id is null"))
            } else {
                movieDao.upsertAll(listOf(entity))
                val movie = movieMapper.toDomain(entity, imageBaseUrl())
                movieCache.put(movieId, movie)
                Result.success(movie)
            }
        } catch (e: HttpException) {
            staleMovieFallback(movieId, stored, e)
        } catch (e: IOException) {
            staleMovieFallback(movieId, stored, e)
        } catch (e: SerializationException) {
            staleMovieFallback(movieId, stored, e)
        }
    }

    private suspend fun fetchAndCacheCast(
        movieId: Int,
        storedCast: List<MovieCastEntity>,
    ): Result<List<Actor>, AppError> {
        return try {
            val response = movieApi.getMovieCredits(movieId)
            buildAndSaveCast(movieId, response)
        } catch (e: HttpException) {
            staleCastFallback(movieId, storedCast, e)
        } catch (e: IOException) {
            staleCastFallback(movieId, storedCast, e)
        } catch (e: SerializationException) {
            staleCastFallback(movieId, storedCast, e)
        }
    }

    private suspend fun buildAndSaveCast(
        movieId: Int,
        response: TmdbMovieCreditsResponseDto,
    ): Result<List<Actor>, AppError> {
        val now = clock.nowMillis()
        // Дедупликация по personId (первое вхождение = меньший order).
        val uniqueCast = response.cast.orEmpty()
            .filter { it.id != null }
            .distinctBy { it.id }
        val imageBase = imageBaseUrl()

        if (uniqueCast.isEmpty()) {
            // Пустой cast — валидное состояние (docs/01_concept.md §6.3–6.4).
            movieCastDao.deleteByMovieId(movieId)
            castCache.put(movieId, emptyList())
            return Result.success(emptyList())
        }

        movieCastDao.upsertAll(uniqueCast.mapNotNull { movieCastMapper.toEntity(it, movieId, now) })
        actorDao.upsertAll(uniqueCast.mapNotNull { movieCastMapper.toActorEntity(it, now) })
        movieCastDao.deleteStaleLinks(movieId, uniqueCast.map { it.id!! })

        val actors = uniqueCast
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .map { actorFromDto(it, imageBase) }
        castCache.put(movieId, actors)
        return Result.success(actors)
    }

    // --- Вспомогательные функции ---

    private suspend fun imageBaseUrl(): String = configurationProvider.getImageBaseUrl()

    private fun List<MovieCastEntity>.isFresh(): Boolean {
        val newest = maxOf { it.fetchedAtMillis }
        return CachePolicy.isFresh(newest, CachePolicy.MOVIE_CAST_TTL, clock.nowMillis())
    }

    private suspend fun actorsFromStoredCast(cast: List<MovieCastEntity>): List<Actor> {
        val actorsById = actorDao.getByIds(cast.map { it.personId }).associateBy { it.personId }
        val imageBase = imageBaseUrl()
        return cast
            .sortedWith(storedCastComparator)
            .mapNotNull { row -> actorsById[row.personId] }
            .distinctBy { it.personId }
            .map { personMapper.toDomain(it, imageBase) }
    }

    private fun actorFromDto(dto: TmdbMovieCastMemberDto, imageBaseUrl: String): Actor =
        Actor(
            tmdbId = dto.id ?: 0,
            name = dto.name?.takeIf { it.isNotBlank() }
                ?: dto.originalName?.takeIf { it.isNotBlank() }
                ?: PersonMapper.UNKNOWN_NAME,
            photoUrl = TmdbImageUrls.compose(
                imageBaseUrl,
                TmdbImageUrls.PROFILE_SIZE,
                dto.profilePath,
            ),
        )

    private suspend fun staleMovieFallback(
        movieId: Int,
        stored: MovieEntity?,
        throwable: Throwable,
    ): Result<Movie, AppError> {
        if (stored != null) {
            Timber.w(throwable, "TMDB недоступен для movie %d, использую устаревший Room-кэш", movieId)
            val movie = movieMapper.toDomain(stored, imageBaseUrl())
            movieCache.put(movieId, movie)
            return Result.success(movie)
        }
        return Result.failure(TmdbErrorMapper.toAppError(throwable))
    }

    private suspend fun staleCastFallback(
        movieId: Int,
        storedCast: List<MovieCastEntity>,
        throwable: Throwable,
    ): Result<List<Actor>, AppError> {
        if (storedCast.isNotEmpty()) {
            Timber.w(throwable, "TMDB недоступен для cast %d, использую устаревший Room-кэш", movieId)
            val actors = actorsFromStoredCast(storedCast)
            castCache.put(movieId, actors)
            return Result.success(actors)
        }
        return Result.failure(TmdbErrorMapper.toAppError(throwable))
    }

    private companion object {
        /** Состав из Room: по позициям каста, безпозиционные — в конце. */
        val storedCastComparator = compareBy<MovieCastEntity> { it.order ?: Int.MAX_VALUE }
            .thenBy { it.personId }
    }
}
