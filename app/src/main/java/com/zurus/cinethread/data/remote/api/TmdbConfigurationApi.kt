package com.zurus.cinethread.data.remote.api

import com.zurus.cinethread.data.remote.dto.TmdbConfigurationResponseDto
import retrofit2.http.GET

/** Конфигурация TMDB: базовые URL изображений (docs/03_data_layer.md §3). */
interface TmdbConfigurationApi {

    @GET("configuration")
    suspend fun getConfiguration(): TmdbConfigurationResponseDto
}
