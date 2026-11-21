package com.zurus.kinosvyazi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Запись `cast` из `/person/{person_id}/movie_credits`: фильм + роль актёра
 * (docs/03_data_layer.md §1). Маппится в `MovieEntity` + `ActorMovieCreditEntity`.
 */
@Serializable
data class TmdbPersonMovieCreditDto(
    val id: Int? = null,
    val title: String? = null,
    @kotlinx.serialization.SerialName("original_title")
    val originalTitle: String? = null,
    @kotlinx.serialization.SerialName("release_date")
    val releaseDate: String? = null,
    @kotlinx.serialization.SerialName("poster_path")
    val posterPath: String? = null,
    val character: String? = null,
    @kotlinx.serialization.SerialName("credit_id")
    val creditId: String? = null,
    val adult: Boolean? = null,
)
