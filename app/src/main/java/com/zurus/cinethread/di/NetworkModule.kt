package com.zurus.cinethread.di

import com.zurus.cinethread.BuildConfig
import com.zurus.cinethread.data.network.NetworkConfig
import com.zurus.cinethread.data.remote.TmdbConfigurationProvider
import com.zurus.cinethread.data.remote.TmdbConfigurationProviderImpl
import com.zurus.cinethread.data.remote.api.TmdbConfigurationApi
import com.zurus.cinethread.data.remote.api.TmdbMovieApi
import com.zurus.cinethread.data.remote.api.TmdbPersonApi
import com.zurus.cinethread.data.remote.interceptor.ApiKeyInterceptor
import com.zurus.cinethread.data.remote.interceptor.RateLimitInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Сетевой слой TMDB: OkHttp (таймауты + api_key), Retrofit (kotlinx-serialization)
 * и провайдер конфигурации изображений (docs/03_data_layer.md §3).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(ApiKeyInterceptor(BuildConfig.TMDB_API_KEY))
            .addInterceptor(RateLimitInterceptor())
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor { message -> Timber.tag("TMDB").d(message) }
                .setLevel(HttpLoggingInterceptor.Level.BASIC)
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(NetworkConfig.TMDB_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()

    @Provides
    fun provideTmdbPersonApi(retrofit: Retrofit): TmdbPersonApi =
        retrofit.create(TmdbPersonApi::class.java)

    @Provides
    fun provideTmdbMovieApi(retrofit: Retrofit): TmdbMovieApi =
        retrofit.create(TmdbMovieApi::class.java)

    @Provides
    @Singleton
    fun provideTmdbConfigurationApi(retrofit: Retrofit): TmdbConfigurationApi =
        retrofit.create(TmdbConfigurationApi::class.java)

    @Provides
    @Singleton
    fun provideTmdbConfigurationProvider(api: TmdbConfigurationApi): TmdbConfigurationProvider =
        TmdbConfigurationProviderImpl(api)

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
}
