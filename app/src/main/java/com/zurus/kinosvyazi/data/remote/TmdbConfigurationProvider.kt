package com.zurus.kinosvyazi.data.remote

import com.zurus.kinosvyazi.data.cache.CachePolicy
import com.zurus.kinosvyazi.data.remote.api.TmdbConfigurationApi
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

/**
 * Кэширует конфигурацию изображений TMDB (24 часа в памяти,
 * docs/03_data_layer.md §5) и собирает полные URL постеров/фото.
 *
 * `profilePath`/`posterPath` из ответов TMDB — относительные пути;
 * полный URL = `secure_base_url` + размер + путь.
 */
class TmdbConfigurationProvider(
    private val configurationApi: TmdbConfigurationApi,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedAtMillis: Long = 0L

    /** Возвращает базовый URL изображений; при недоступности TMDB — fallback-константу. */
    suspend fun getImageBaseUrl(): String {
        val cached = cachedBaseUrl
        if (cached != null && CachePolicy.isFresh(cachedAtMillis, CachePolicy.TMDB_CONFIGURATION_TTL, nowMillis())) {
            return cached
        }
        val fetched = try {
            configurationApi.getConfiguration().images?.secureBaseUrl
                ?.takeIf { it.isNotBlank() }
        } catch (e: HttpException) {
            Timber.w(e, "TMDB /configuration недоступен, использую дефолтный базовый URL изображений")
            null
        } catch (e: IOException) {
            Timber.w(e, "TMDB /configuration недоступен, использую дефолтный базовый URL изображений")
            null
        } catch (e: SerializationException) {
            Timber.w(e, "Некорректный ответ TMDB /configuration, использую дефолтный базовый URL")
            null
        }
        val base = normalizeBase(fetched ?: TmdbImageUrls.DEFAULT_BASE_URL)
        cachedBaseUrl = base
        cachedAtMillis = nowMillis()
        return base
    }

    private fun normalizeBase(raw: String): String =
        if (raw.endsWith("/")) raw else "$raw/"

    /** Размеры и fallback для URL изображений TMDB. */
    object TmdbImageUrls {
        const val DEFAULT_BASE_URL = "https://image.tmdb.org/t/p/"
        const val POSTER_SIZE = "w342"
        const val PROFILE_SIZE = "w185"

        fun compose(baseUrl: String, size: String, path: String?): String? =
            path?.takeIf { it.isNotBlank() }?.let { "$baseUrl$size$it" }
    }
}
