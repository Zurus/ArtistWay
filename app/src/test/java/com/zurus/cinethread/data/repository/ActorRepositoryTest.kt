package com.zurus.cinethread.data.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.data.local.entity.ActorMovieCreditEntity
import com.zurus.cinethread.data.local.entity.MovieEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Year

/**
 * Unit-тесты `ActorRepositoryImpl` (docs/05_roadmap.md, Этап 2):
 * цепочка in-memory → Room → TMDB, TTL, fallback на устаревший кэш,
 * повторный вызов без сети, классификация ошибок, 429-повторы.
 */
@RunWith(RobolectricTestRunner::class)
class ActorRepositoryTest : TmdbRepositoryTest() {

    @Test
    fun getActorMovies_success_savesToRoomAndReturnsDomain() = runBlocking {
        enqueue200(
            filmographyResponse(
                creditJson(201, "Movie A", "2020-05-10", posterPath = "/poster-a.jpg"),
                creditJson(202, "Movie B", "2018-01-01"),
            ),
        )

        val result = actorRepository().getActorMovies(PERSON_ID)

        assertTrue("Ожидался Success, получен $result", result is Result.Success)
        val movies = (result as Result.Success).value
        assertEquals(2, movies.size)
        assertEquals("Movie A", movies[0].title)
        assertEquals(Year.of(2020), movies[0].year)
        assertEquals("${imageBaseUrl}w342/poster-a.jpg", movies[0].posterUrl)
        assertEquals(2, actorMovieCreditDao.getByPersonId(PERSON_ID).size)
        assertEquals(2, movieDao.getByIds(listOf(201, 202)).size)
    }

    @Test
    fun getActorMovies_secondCall_noNetworkRequest() = runBlocking {
        enqueue200(filmographyResponse(creditJson(201, "Movie A", "2020-05-10")))
        val repository = actorRepository()

        val first = repository.getActorMovies(PERSON_ID)
        val second = repository.getActorMovies(PERSON_ID)

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals("Второй вызов должен идти из кэша, без TMDB", 1, requestCount())
    }

    @Test
    fun getActorMovies_staleRoom_freshNetwork_refreshesCache() = runBlocking {
        insertStaleFilmography(301, "Stale Movie")
        enqueue200(filmographyResponse(creditJson(302, "Fresh Movie", "2023-01-01")))

        val result = actorRepository().getActorMovies(PERSON_ID)

        val movies = (result as Result.Success).value
        assertEquals("Устаревший кэш должен быть обновлён из TMDB", listOf("Fresh Movie"), movies.map { it.title })
        assertEquals(1, requestCount())
        assertEquals("Fresh Movie", movieDao.getById(302)?.title)
    }

    @Test
    fun getActorMovies_staleRoom_networkDown_returnsStaleCache() = runBlocking {
        insertStaleFilmography(301, "Stale Movie")
        enqueueError(500)

        val result = actorRepository().getActorMovies(PERSON_ID)

        val movies = (result as Result.Success).value
        assertEquals("При недоступном TMDB отдаётся устаревший кэш", listOf("Stale Movie"), movies.map { it.title })
    }

    @Test
    fun getActorMovies_emptyCast_successEmptyList() = runBlocking {
        enqueue200("""{"id":$PERSON_ID,"cast":[]}""")

        val result = actorRepository().getActorMovies(PERSON_ID)

        assertTrue(result.isSuccess)
        assertTrue("Ожидался пустой список", result.getOrNull().orEmpty().isEmpty())
        assertTrue("Связи с фильмами удалены", actorMovieCreditDao.getByPersonId(PERSON_ID).isEmpty())
    }

    @Test
    fun getActorMovies_missingFields_placeholdersAndNullIdSkipped() = runBlocking {
        enqueue200(
            filmographyResponse(
                creditJson(201, title = null, releaseDate = null, posterPath = null),
                """{"character":"NoId Role"}""",
            ),
        )

        val result = actorRepository().getActorMovies(PERSON_ID)

        val movies = (result as Result.Success).value
        assertEquals("Запись без id не сохраняется", 1, movies.size)
        assertEquals("Без названия", movies[0].title)
        assertNull("Год из отсутствующей releaseDate не извлекается", movies[0].year)
        assertNull(movies[0].posterUrl)
        assertEquals(1, actorMovieCreditDao.getByPersonId(PERSON_ID).size)
    }

    @Test
    fun getActorMovies_duplicateMovieId_deduped() = runBlocking {
        enqueue200(
            filmographyResponse(
                creditJson(201, "Movie A", "2020-05-10", character = "Role 1"),
                creditJson(201, "Movie A", "2020-05-10", character = "Role 2"),
            ),
        )

        val result = actorRepository().getActorMovies(PERSON_ID)

        val movies = (result as Result.Success).value
        assertEquals("Один фильм — одна запись в ответе", 1, movies.size)
        assertEquals("Обе роли актёра сохранены в Room", 2, actorMovieCreditDao.getByPersonId(PERSON_ID).size)
    }

    @Test
    fun getActor_success_returnsDomainActor() = runBlocking {
        enqueue200(personJson(PERSON_ID, "Some Actor", "/profile.jpg"))

        val result = actorRepository().getActor(PERSON_ID)

        val actor = (result as Result.Success).value
        assertEquals(PERSON_ID, actor.tmdbId)
        assertEquals("Some Actor", actor.name)
        assertEquals("${imageBaseUrl}w185/profile.jpg", actor.photoUrl)
        assertNotNull(actorDao.getById(PERSON_ID))
    }

    @Test
    fun getActor_429Then200_retriesAndSucceeds() = runBlocking {
        enqueue429(0)
        enqueue200(personJson(PERSON_ID, "Some Actor", null))

        val result = actorRepository().getActor(PERSON_ID)

        assertTrue("После 429 с Retry-After запрос повторяется", result.isSuccess)
        assertEquals(2, requestCount())
    }

    @Test
    fun getActor_429RetriesExhausted_rateLimitError() = runBlocking {
        repeat(4) { enqueue429(0) }

        val result = actorRepository().getActor(PERSON_ID)

        val error = (result as Result.Failure).error
        assertTrue("Ожидался RateLimit, получен $error", error is AppError.RateLimit)
        assertEquals("1 + 3 повтора", 4, requestCount())
    }

    @Test
    fun getActor_500_serverError() = runBlocking {
        enqueueError(500)

        val result = actorRepository().getActor(PERSON_ID)

        assertEquals(AppError.Server, (result as Result.Failure).error)
    }

    @Test
    fun getActor_timeout_networkError() = runBlocking {
        hangServer(HANG_MILLIS)

        val result = actorRepository(client(readTimeoutMillis = 200)).getActor(PERSON_ID)

        val error = (result as Result.Failure).error
        assertTrue("Таймаут — Network/Offline, получен $error", error == AppError.Network || error == AppError.Offline)
    }

    @Test
    fun getActor_noCache_networkDown_error() = runBlocking {
        enqueueError(500)

        val result = actorRepository().getActor(PERSON_ID)

        assertEquals("Без кэша и без сети — ошибка", AppError.Server, (result as Result.Failure).error)
    }

    // --- Фикстуры ---

    private suspend fun insertStaleFilmography(movieId: Int, title: String) {
        val staleAt = currentNow - STALE_AGE_MILLIS
        movieDao.upsertAll(
            listOf(
                MovieEntity(
                    movieId = movieId,
                    title = title,
                    originalTitle = null,
                    overview = null,
                    releaseDate = null,
                    posterPath = null,
                    backdropPath = null,
                    runtime = null,
                    voteAverage = null,
                    voteCount = null,
                    adult = null,
                    fetchedAtMillis = staleAt,
                )
            )
        )
        actorMovieCreditDao.upsertAll(
            listOf(
                ActorMovieCreditEntity(
                    personId = PERSON_ID,
                    movieId = movieId,
                    character = null,
                    creditId = null,
                    fetchedAtMillis = staleAt,
                )
            )
        )
    }

    private fun filmographyResponse(vararg credits: String): String =
        """{"id":$PERSON_ID,"cast":[${credits.joinToString(",")}]}"""

    private fun creditJson(
        movieId: Int,
        title: String?,
        releaseDate: String?,
        posterPath: String? = null,
        character: String? = "Role",
    ): String {
        val fields = buildList {
            add("\"id\":$movieId")
            title?.let { add("\"title\":\"$it\"") }
            releaseDate?.let { add("\"release_date\":\"$it\"") }
            posterPath?.let { add("\"poster_path\":\"$it\"") }
            character?.let { add("\"character\":\"$it\"") }
        }
        return "{${fields.joinToString(",")}}"
    }

    private fun personJson(personId: Int, name: String?, profilePath: String?): String = buildString {
        append("{")
        append("\"id\":$personId")
        name?.let { append(",\"name\":\"$it\"") }
        profilePath?.let { append(",\"profile_path\":\"$it\"") }
        append(",\"known_for_department\":\"Acting\"")
        append(",\"popularity\":1.5")
        append("}")
    }

    private companion object {
        const val PERSON_ID = 101
        const val STALE_AGE_MILLIS = 8L * 24 * 60 * 60 * 1000
        const val HANG_MILLIS = 2_000L
    }
}
