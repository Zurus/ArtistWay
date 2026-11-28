package com.zurus.cinethread.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * D6 (docs/debugging.md): файловый лог запросов/ответов TMDB-клиента.
 *
 * Флаг включён → каждый запрос дописывается в [file]
 * (обычно `<filesDir>/tmdb_requests.log`): время, метод, URL с
 * замаскированным ключом (`api_key=***`), HTTP-код, длительность,
 * размеры; тело запроса (если есть) и тела ответа — с обрезкой
 * до [MAX_BODY_LOG_BYTES]. Флаг выключен → `chain.proceed()`,
 * нулевой overhead (в DI при выключенном флаге интерцептор
 * не ставится вовсе).
 *
 * Гарантии:
 * - API-ключ в лог ни при каких условиях не попадает (маскировка
 *   `api_key=…` в URL);
 * - запись не подвешивает запросы: тело ответа читается через
 *   `peekBody` (оригинальный поток не затрагивается), тело запроса —
 *   только если размер известен и ≤ [MAX_REQUEST_BODY_BYTES];
 *   сбой записи игнорируется;
 * - ротация: файл крупнее ~[MAX_LOG_FILE_BYTES] переносится в
 *   `<имя>.1` (свежий остаётся), старые перезаписываются.
 */
class TmdbRequestLogInterceptor(
    private val enabled: Boolean,
    private val file: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled) return chain.proceed(chain.request())

        val request = chain.request()
        // Тело запроса (если есть) копируем в память один раз и
        // воспроизводим на сервер — оригинальный поток не трогаем.
        val bodyInfo = safeRequestBody(request.body)
        val safeRequest = if (bodyInfo.replay != null) {
            // OkHttp 4: у Builder нет body() — метод+тело ставятся через method().
            request.newBuilder().method(request.method, bodyInfo.replay).build()
        } else {
            request
        }

        val start = nowMillis()
        val response = chain.proceed(safeRequest)
        val durationMs = nowMillis() - start
        runCatching { logEntry(request, response, durationMs, bodyInfo) }
        return response
    }

    /**
     * Запись лога:
     * `2026-11-28 13:00:00.123 | GET https://…?api_key=*** | 200 | 1234 ms | req 0 B | res 54321 B`
     * + строки `req body: …` / `res body: …` (обрезка до [MAX_BODY_LOG_BYTES]).
     */
    private fun logEntry(
        request: Request,
        response: Response,
        durationMs: Long,
        bodyInfo: BodyInfo,
    ) {
        val maskedUrl = maskSecrets(request.url.toString())
        val responseBody = response.body
        // peekBody: отдельное тело, читающее ≤ лимита из оригинального —
        // ответ для вызывающего остаётся нетронутым.
        val resBytes = response.peekBody(MAX_BODY_LOG_BYTES.toLong()).bytes()
        val resTruncated = responseBody?.contentLength()?.let { it > MAX_BODY_LOG_BYTES.toLong() }
            ?: (resBytes.size >= MAX_BODY_LOG_BYTES)

        val entry = buildString {
            append(timestamp(nowMillis()))
            append(" | ")
            append(request.method)
            append(' ')
            append(maskedUrl)
            append(" | ")
            append(response.code)
            append(" | ")
            append(durationMs)
            append(" ms | req ")
            append(bodyInfo.bytes?.size ?: 0)
            append(" B | res ")
            append(responseBody?.contentLength()?.coerceAtLeast(0) ?: resBytes.size)
            append(" B\n")
            append("req body: ")
            append(bodyInfo.text ?: "-")
            append("\nres body: ")
            append(resBytes.toString(Charsets.UTF_8))
            if (resTruncated) append("…[truncated]")
            append('\n')
        }
        appendWithRotation(entry)
    }

    /**
     * Копия тела запроса: [BodyInfo.replay] — [RequestBody] для
     * воспроизведения, [BodyInfo.text] — текст для лога.
     * Потоковые тела неизвестного размера не читаем (риск подвисания)
     * — в лог уходит пометка, запрос уходит в исходном виде.
     */
    private fun safeRequestBody(body: RequestBody?): BodyInfo {
        if (body == null) return BodyInfo(null, null, null)
        val length = body.contentLength()
        if (length !in 0..MAX_REQUEST_BODY_BYTES) {
            return BodyInfo(null, null, "<skipped: stream body>")
        }
        val buffer = Buffer()
        body.writeTo(buffer)
        val bytes = buffer.readByteArray()
        @Suppress("DEPRECATION")
        val replay = RequestBody.create(body.contentType(), bytes)
        return BodyInfo(
            replay = replay,
            bytes = bytes,
            text = truncated(bytes.toString(Charsets.UTF_8)),
        )
    }

    /** Обрезка текста для лога до [MAX_BODY_LOG_BYTES]. */
    private fun truncated(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return if (bytes.size <= MAX_BODY_LOG_BYTES) {
            text
        } else {
            bytes.copyOfRange(0, MAX_BODY_LOG_BYTES).toString(Charsets.UTF_8) +
                "…[truncated ${bytes.size - MAX_BODY_LOG_BYTES} bytes]"
        }
    }

    /** Доп. запись с ротацией: крупный файл → `<имя>.1`, свежий — с нуля. */
    private fun appendWithRotation(entry: String) {
        synchronized(lock) {
            if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                runCatching {
                    file.copyTo(File(file.parentFile, file.name + ROTATED_SUFFIX), overwrite = true)
                    file.delete()
                }
            }
            file.appendText(entry)
        }
    }

    /** API-ключ не попадает в лог: `api_key=…` в URL маскируем. */
    private fun maskSecrets(url: String): String =
        url.replace(API_KEY_QUERY, "api_key=***")

    private fun timestamp(millis: Long): String =
        SimpleDateFormat(TIME_FORMAT, Locale.US).format(Date(millis))

    private data class BodyInfo(
        val replay: RequestBody?,
        val bytes: ByteArray?,
        val text: String?,
    )

    companion object {
        /** Обрезка тел в лог (≈ 200 КБ). */
        const val MAX_BODY_LOG_BYTES = 200 * 1024

        /** Тела запроса читаем в память только до этого размера. */
        private const val MAX_REQUEST_BODY_BYTES = 1024 * 1024

        /** Ротация: крупнее ~10 МБ — переносим в `.1`. */
        private const val MAX_LOG_FILE_BYTES = 10L * 1024 * 1024

        private const val ROTATED_SUFFIX = ".1"
        private const val TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private val API_KEY_QUERY = Regex("api_key=[^&\\s]+")
        private val lock = Any()
    }
}
