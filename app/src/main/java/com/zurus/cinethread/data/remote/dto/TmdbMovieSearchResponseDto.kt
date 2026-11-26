package com.zurus.cinethread.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ответ `/search/movie` (docs/03_data_layer.md §1).
 * Не используется в базовом игровом цикле MVP, интерфейс зарезервирован.
 */
@Serializable
data class TmdbMovieSearchResponseDto(
    val page: Int? = null,
    val results: List<TmdbMovieDto>? = null,
    @SerialName("total_pages")
    val totalPages: Int? = null,
    @SerialName("total_results")
    val totalResults: Int? = null,
)
