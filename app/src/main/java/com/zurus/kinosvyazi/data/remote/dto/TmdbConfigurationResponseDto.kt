package com.zurus.kinosvyazi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Ответ `/configuration` — базовые URL изображений TMDB
 * (docs/03_data_layer.md §1: нужен для сборки URL постеров и фото).
 */
@Serializable
data class TmdbConfigurationResponseDto(
    val images: TmdbImagesConfigurationDto? = null,
)

@Serializable
data class TmdbImagesConfigurationDto(
    @kotlinx.serialization.SerialName("base_url")
    val baseUrl: String? = null,
    @kotlinx.serialization.SerialName("secure_base_url")
    val secureBaseUrl: String? = null,
    @kotlinx.serialization.SerialName("poster_sizes")
    val posterSizes: List<String>? = null,
    @kotlinx.serialization.SerialName("profile_sizes")
    val profileSizes: List<String>? = null,
)
