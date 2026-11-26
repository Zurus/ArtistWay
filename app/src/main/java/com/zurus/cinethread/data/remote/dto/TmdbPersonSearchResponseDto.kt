package com.zurus.cinethread.data.remote.dto

import kotlinx.serialization.Serializable

/** Ответ `/search/person` (docs/03_data_layer.md §1). */
@Serializable
data class TmdbPersonSearchResponseDto(
    val page: Int? = null,
    val results: List<TmdbPersonDto>? = null,
    @kotlinx.serialization.SerialName("total_pages")
    val totalPages: Int? = null,
    @kotlinx.serialization.SerialName("total_results")
    val totalResults: Int? = null,
)
