package com.zurus.cinethread.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zurus.cinethread.data.local.dao.ActorDao
import com.zurus.cinethread.data.local.dao.ActorMovieCreditDao
import com.zurus.cinethread.data.local.dao.GameSessionDao
import com.zurus.cinethread.data.local.dao.MovieCastDao
import com.zurus.cinethread.data.local.dao.MovieDao
import com.zurus.cinethread.data.local.database.AppDatabase
import com.zurus.cinethread.data.mapper.GameSessionMapper
import com.zurus.cinethread.data.mapper.MovieCastMapper
import com.zurus.cinethread.data.mapper.MovieMapper
import com.zurus.cinethread.data.mapper.PersonMapper
import com.zurus.cinethread.data.mapper.PersonMovieCreditMapper
import com.zurus.cinethread.data.remote.TmdbConfigurationProvider
import com.zurus.cinethread.data.remote.api.TmdbMovieApi
import com.zurus.cinethread.data.remote.api.TmdbPersonApi
import com.zurus.cinethread.data.remote.interceptor.ApiKeyInterceptor
import com.zurus.cinethread.data.remote.interceptor.RateLimitInterceptor
import com.zurus.cinethread.data.time.Clock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * База для тестов репозиториев data layer (docs/05_roadmap.md, Этап 2):
 * MockWebServer вместо TMDB + Room в памяти (Robolectric) + управляемые часы.
 *
 * Интерцептор 429-повторов ставится с нулевой задержкой: сценарии
 * проверяются по числу запросов, а не по времени.
 */
@RunWith(RobolectricTestRunner::class)
abstract class TmdbRepositoryTest {

    protected val fixedNow = 1_700_000_000_000L
    protected var currentNow = fixedNow

    protected val testClock: Clock = Clock {
        currentNow
    }

    protected lateinit var server: MockWebServer
    protected lateinit var database: AppDatabase

    protected lateinit var actorDao: ActorDao
    protected lateinit var movieDao: MovieDao
    protected lateinit var movieCastDao: MovieCastDao
    protected lateinit var actorMovieCreditDao: ActorMovieCreditDao
    protected lateinit var gameSessionDao: GameSessionDao

    protected val imageBaseUrl = "https://image.tmdb.org/t/p/"
    private val configurationProvider = object : TmdbConfigurationProvider {
        override suspend fun getImageBaseUrl(): String = imageBaseUrl
    }

    @Before
    open fun setUp() {
        currentNow = fixedNow
        server = MockWebServer()
        server.start()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        actorDao = database.actorDao()
        movieDao = database.movieDao()
        movieCastDao = database.movieCastDao()
        actorMovieCreditDao = database.actorMovieCreditDao()
        gameSessionDao = database.gameSessionDao()
    }

    @After
    open fun tearDown() {
        database.close()
        server.shutdown()
    }

    // --- Клиент и API ---

    protected fun client(readTimeoutMillis: Long = READ_TIMEOUT_MILLIS): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(ApiKeyInterceptor("test-key"))
            .addInterceptor(RateLimitInterceptor(sleeper = { /* без реальных задержек в тестах */ }))
            .build()

    protected fun retrofit(client: OkHttpClient = client()): Retrofit =
        Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()

    protected fun personApi(client: OkHttpClient = client()): TmdbPersonApi =
        retrofit(client).create(TmdbPersonApi::class.java)

    protected fun movieApi(client: OkHttpClient = client()): TmdbMovieApi =
        retrofit(client).create(TmdbMovieApi::class.java)

    // --- Репозитории ---

    protected fun actorRepository(client: OkHttpClient = client()): ActorRepositoryImpl =
        ActorRepositoryImpl(
            personApi(client),
            configurationProvider,
            actorDao,
            movieDao,
            actorMovieCreditDao,
            PersonMapper.Default(),
            MovieMapper.Default(),
            PersonMovieCreditMapper.Default(),
            testClock,
        )

    protected fun movieRepository(client: OkHttpClient = client()): MovieRepositoryImpl =
        MovieRepositoryImpl(
            movieApi(client),
            configurationProvider,
            movieDao,
            movieCastDao,
            actorDao,
            MovieMapper.Default(),
            PersonMapper.Default(),
            MovieCastMapper.Default(),
            testClock,
        )

    protected fun gameSessionRepository(): GameSessionRepositoryImpl =
        GameSessionRepositoryImpl(
            gameSessionDao,
            actorDao,
            configurationProvider,
            GameSessionMapper.Default(PersonMapper.Default()),
            testClock,
        )

    // --- Ответы MockWebServer ---

    protected fun enqueue200(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    protected fun enqueueError(statusCode: Int) {
        server.enqueue(MockResponse().setResponseCode(statusCode).setBody("{}"))
    }

    protected fun enqueue429(retryAfterSeconds: Int?) {
        val response = MockResponse().setResponseCode(429).setBody("{}")
        retryAfterSeconds?.let { response.setHeader("Retry-After", it.toString()) }
        server.enqueue(response)
    }

    protected fun requestCount(): Int = server.requestCount

    /** Сервер «зависает»: не отвечает, пока клиент не прервёт чтение. */
    protected fun hangServer(hangMillis: Long) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Thread.sleep(hangMillis)
                return MockResponse().setBody("{}")
            }
        }
    }

    private companion object {
        const val READ_TIMEOUT_MILLIS = 5_000L
        const val CONNECT_TIMEOUT_SECONDS = 5L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}
