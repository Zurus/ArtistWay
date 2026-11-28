package com.zurus.cinethread.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D6 (docs/debugging.md): файловый лог запросов TMDB.
 * Флаг и файл — параметры интерцептора; сервер — MockWebServer,
 * каталог — временный.
 */
class TmdbRequestLogInterceptorTest {

    private val server = MockWebServer()
    private var tempDir: File? = null

    @BeforeTest
    fun setUp() {
        server.start()
        tempDir = createTempDirectory("tmdb-log-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
        tempDir?.deleteRecursively()
    }

    private fun logFile(name: String): File = File(tempDir!!, name)

    private fun client(interceptor: Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(SECRET_KEY))
            .addInterceptor(interceptor)
            .build()

    private fun loggingInterceptor(file: File) =
        TmdbRequestLogInterceptor(true, file, nowMillis = { FIXED_NOW_MS })

    private fun get(path: String = "/test"): Request =
        Request.Builder().url(server.url(path)).build()

    @Test
    fun `200 is logged with code and duration, key masked, body present`() {
        server.enqueue(MockResponse().setBody("""{"id":1,"title":"Inception"}"""))
        val file = logFile("log-200")

        val response = client(loggingInterceptor(file)).newCall(get()).execute()

        assertEquals(200, response.code)
        assertTrue(file.exists(), "файл лога создан")
        val log = file.readText()
        assertTrue("GET" in log, "метод в логе")
        assertTrue(
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}""").containsMatchIn(log),
            "временная метка в логе: $log",
        )
        assertTrue("api_key=***" in log, "ключ замаскирован: $log")
        assertFalse(SECRET_KEY in log, "сырой ключ в лог не попал")
        assertTrue("200" in log, "HTTP-код в логе")
        assertTrue("0 ms" in log, "длительность в логе")
        assertTrue("\"title\":\"Inception\"" in log, "тело ответа в логе")
    }

    @Test
    fun `response body is truncated to 200KB`() {
        val bigBody = "x".repeat(300 * 1024)
        server.enqueue(MockResponse().setBody(bigBody))
        val file = logFile("log-truncated")

        client(loggingInterceptor(file)).newCall(get()).execute()

        val log = file.readText()
        assertTrue("…[truncated]" in log, "маркер обрезки в логе")
        val loggedX = log.count { it == 'x' }
        assertTrue(
            loggedX <= TmdbRequestLogInterceptor.MAX_BODY_LOG_BYTES,
            "записано ≤ лимита тел: $loggedX",
        )
    }

    @Test
    fun `flag off — no file, request passes through`() {
        server.enqueue(MockResponse().setBody("{}"))
        val file = logFile("log-off")

        val response = client(
            TmdbRequestLogInterceptor(false, file, nowMillis = { FIXED_NOW_MS }),
        ).newCall(get()).execute()

        assertEquals(200, response.code)
        assertFalse(file.exists(), "файл не создаётся при выключенном флаге")
    }

    @Test
    fun `request body is logged and replayed intact`() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val body = """{"hello":"world","n":42}"""
        val file = logFile("log-post")
        val request = Request.Builder()
            .url(server.url("/post"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client(loggingInterceptor(file)).newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(
            body,
            server.takeRequest().body.readUtf8(),
            "сервер получил тело целиком (replay работает)",
        )
        assertTrue("\"hello\":\"world\"" in file.readText(), "тело запроса в логе")
    }

    @Test
    fun `file over 10MB is rotated to backup`() {
        val file = logFile("log-rotated")
        file.writeText("a".repeat(ROTATE_THRESHOLD_BYTES + 1))
        server.enqueue(MockResponse().setBody("{}"))

        client(loggingInterceptor(file)).newCall(get()).execute()

        val rotated = File(file.parentFile, file.name + ".1")
        assertTrue(rotated.exists(), "старый файл перенесён в .1")
        assertEquals(ROTATE_THRESHOLD_BYTES + 1L, rotated.length())
        assertTrue(file.length() < 1024, "свежий лог маленький: ${file.length()}")
    }

    @Test
    fun `log write failure does not break the call`() {
        server.enqueue(MockResponse().setBody("{}"))
        // Каталог-файл: в него нельзя создать файл — запись упадёт.
        val blocker = logFile("blocker")
        blocker.writeText("x")
        val badFile = File(blocker, "impossible")
        val interceptor = TmdbRequestLogInterceptor(true, badFile, nowMillis = { FIXED_NOW_MS })

        val response = client(interceptor).newCall(get()).execute()

        assertEquals(200, response.code, "сбой записи лога не ломает запрос")
    }

    private companion object {
        const val SECRET_KEY = "SECRET_KEY_123"
        const val FIXED_NOW_MS = 1_700_000_000_123L

        /** ~10 МБ — порог ротации (см. TmdbRequestLogInterceptor). */
        const val ROTATE_THRESHOLD_BYTES = 10 * 1024 * 1024
    }
}
