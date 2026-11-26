package com.zurus.cinethread.data.repository

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.data.local.entity.ActorEntity
import com.zurus.cinethread.data.local.entity.MovieCastEntity
import com.zurus.cinethread.data.local.entity.MovieEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Year

/**
 * Unit-тесты `MovieRepositoryImpl` (docs/05_roadmap.md, Этап 2):
 * состав только из `cast` (`crew` игнорируется), дедупликация,
 * пустой `cast` → `Success(emptyList())`, TTL и fallback, `id = null`.
 */
@RunWith(RobolectricTestRunner::class)
class MovieRepositoryTest : TmdbRepositoryTest() {

    @Test
    fun getMovieCast_success_ignoresCrew_savesCastAndActors() = runBlocking {
        enqueue200(
            movieCreditsResponse(
                castMemberJson(301, "Actor A", order = 0, profilePath = "/a.jpg"),
                castMemberJson(302, "Actor B", order = 1),
                castMemberJson(303, "Actor C", order = 2),
                crewJson = """{"id":900,"name":"Director","job":"Director"}""",
            ),
        )

        val result = movieRepository().getMovieCast(MOVIE_ID)

        val actors = (result as Result.Success).value
        assertEquals(listOf("Actor A", "Actor B", "Actor C"), actors.map { it.name })
        assertEquals("${imageBaseUrl}w185/a.jpg", actors[0].photoUrl)
        assertEquals(3, movieCastDao.getByMovieId(MOVIE_ID).size)
        assertEquals(3, actorDao.getByIds(listOf(301, 302, 303)).size)
        assertNull("Crew не сохраняется как состав", actorDao.getById(900))
    }

    @Test
    fun getMovieCast_duplicatePersonId_deduped() = runBlocking {
        enqueue200(
            movieCreditsResponse(
                castMemberJson(301, "Actor A", order = 0),
                castMemberJson(302, "Actor B", order = 1),
                castMemberJson(301, "Actor A", order = 3),
            ),
        )

        val result = movieRepository().getMovieCast(MOVIE_ID)

        val actors = (result as Result.Success).value
        assertEquals("Один актёр — одна запись в ответе", listOf(301, 302), actors.map { it.tmdbId })
        assertEquals(2, movieCastDao.getByMovieId(MOVIE_ID).size)
    }

    @Test
    fun getMovieCast_emptyCast_successEmptyListAndClearsStale() = runBlocking {
        insertStaleCast()
        enqueue200("""{"id":$MOVIE_ID,"title":"Movie","cast":[]}""")

        val result = movieRepository().getMovieCast(MOVIE_ID)

        assertTrue("Пустой cast — валидное состояние", result.getOrNull().orEmpty().isEmpty())
        assertTrue("Старые строки состава очищены", movieCastDao.getByMovieId(MOVIE_ID).isEmpty())
    }

    @Test
    fun getMovieCast_nullIdSkipped() = runBlocking {
        enqueue200(
            movieCreditsResponse(
                """{"name":"NoId Actor","order":0}""",
                castMemberJson(301, "Actor A", order = 1),
            ),
        )

        val result = movieRepository().getMovieCast(MOVIE_ID)

        val actors = (result as Result.Success).value
        assertEquals("Запись без id игнорируется", listOf(301), actors.map { it.tmdbId })
        assertEquals(1, movieCastDao.getByMovieId(MOVIE_ID).size)
    }

    @Test
    fun getMovieCast_secondCall_noNetworkRequest() = runBlocking {
        enqueue200(movieCreditsResponse(castMemberJson(301, "Actor A", order = 0)))
        val repository = movieRepository()

        val first = repository.getMovieCast(MOVIE_ID)
        val second = repository.getMovieCast(MOVIE_ID)

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals("Второй вызов должен идти из кэша, без TMDB", 1, requestCount())
    }

    @Test
    fun getMovieCast_staleRoom_networkDown_returnsStaleCache() = runBlocking {
        insertStaleCast()
        enqueueError(500)

        val result = movieRepository().getMovieCast(MOVIE_ID)

        val actors = (result as Result.Success).value
        assertEquals("При недоступном TMDB отдаётся устаревший кэш", listOf("Stale Actor"), actors.map { it.name })
    }

    @Test
    fun getMovieCast_noCache_networkDown_error() = runBlocking {
        enqueueError(500)

        val result = movieRepository().getMovieCast(MOVIE_ID)

        assertEquals(AppError.Server, (result as Result.Failure).error)
    }

    @Test
    fun getMovie_success_savesToRoomAndCaches() = runBlocking {
        enqueue200(movieJson(MOVIE_ID, "Batman Begins", "2005-06-15", posterPath = "/batman.jpg"))
        val repository = movieRepository()

        val first = repository.getMovie(MOVIE_ID)
        val second = repository.getMovie(MOVIE_ID)

        val movie = (first as Result.Success).value
        assertEquals("Batman Begins", movie.title)
        assertEquals(Year.of(2005), movie.year)
        assertEquals("${imageBaseUrl}w342/batman.jpg", movie.posterUrl)
        assertTrue(second.isSuccess)
        assertEquals("Второй вызов — из кэша", 1, requestCount())
        assertEquals("Batman Begins", movieDao.getById(MOVIE_ID)?.title)
    }

    @Test
    fun getMovie_staleRoom_networkDown_returnsStaleCache() = runBlocking {
        movieDao.upsertAll(
            listOf(
                MovieEntity(
                    movieId = MOVIE_ID,
                    title = "Stale Movie",
                    originalTitle = null,
                    overview = null,
                    releaseDate = null,
                    posterPath = null,
                    backdropPath = null,
                    runtime = null,
                    voteAverage = null,
                    voteCount = null,
                    adult = null,
                    fetchedAtMillis = currentNow - STALE_AGE_MILLIS,
                )
            )
        )
        enqueueError(500)

        val result = movieRepository().getMovie(MOVIE_ID)

        assertEquals("Stale Movie", (result as Result.Success).value.title)
    }

    @Test
    fun getMovie_500_noCache_serverError() = runBlocking {
        enqueueError(500)

        val result = movieRepository().getMovie(MOVIE_ID)

        assertEquals(AppError.Server, (result as Result.Failure).error)
    }

    // --- Фикстуры ---

    private suspend fun insertStaleCast() {
        val staleAt = currentNow - STALE_AGE_MILLIS
        movieCastDao.upsertAll(
            listOf(
                MovieCastEntity(
                    personId = 301,
                    movieId = MOVIE_ID,
                    character = "Stale Role",
                    creditId = null,
                    order = 0,
                    fetchedAtMillis = staleAt,
                )
            )
        )
        actorDao.upsertAll(
            listOf(
                ActorEntity(
                    personId = 301,
                    name = "Stale Actor",
                    profilePath = null,
                    knownForDepartment = null,
                    popularity = null,
                    fetchedAtMillis = staleAt,
                )
            )
        )
    }

    private fun movieCreditsResponse(vararg castMembers: String, crewJson: String? = null): String {
        val crew = crewJson?.let { ",\"crew\":[$it]" } ?: ""
        return """{"id":$MOVIE_ID,"title":"Movie","cast":[${castMembers.joinToString(",")}]$crew}"""
    }

    private fun castMemberJson(
        personId: Int,
        name: String,
        order: Int,
        character: String? = "Role",
        profilePath: String? = null,
    ): String {
        val fields = buildList {
            add("\"id\":$personId")
            add("\"name\":\"$name\"")
            character?.let { add("\"character\":\"$it\"") }
            profilePath?.let { add("\"profile_path\":\"$it\"") }
            add("\"order\":$order")
            add("\"credit_id\":\"c-$personId\"")
        }
        return "{${fields.joinToString(",")}}"
    }

    private fun movieJson(
        movieId: Int,
        title: String,
        releaseDate: String,
        posterPath: String?,
    ): String = buildString {
        append("{\"id\":$movieId")
        append(",\"title\":\"$title\"")
        append(",\"original_title\":\"$title\"")
        append(",\"release_date\":\"$releaseDate\"")
        if (posterPath != null) {
            append(",\"poster_path\":\"$posterPath\"")
        }
        append(",\"vote_average\":8.1,\"adult\":false}")
    }

    private companion object {
        const val MOVIE_ID = 201
        const val STALE_AGE_MILLIS = 8L * 24 * 60 * 60 * 1000
    }
}
