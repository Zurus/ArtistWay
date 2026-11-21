package com.zurus.kinosvyazi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Данные фильма из `/movie/{movie_id}` (docs/03_data_layer.md §1).
 * Обязательное для игры поле — `id`; `title`/`releaseDate`/`posterPath`
 * могут отсутствовать и обеспечиваются маппером.
 */
@Serializable
data class TmdbMovieDto(
    val id: Int? = null,
    val title: String? = null,
    @kotlinx.serialization.SerialName("original_title")
    val originalTitle: String? = null,
    val overview: String? = null,
    @kotlinx.serialization.SerialName("release_date")
    val releaseDate: String? = null,
    @kotlinx.serialization.SerialName("poster_path")
    val posterPath: String? = null,
    @kotlinx.serialization.SerialName("backdrop_path")
    val backdropPath: String? = null,
    val runtime: Int? = null,
    @kotlinx.serialization.SerialName("vote_average")
    val voteAverage: Double? = null,
    @kotlinx.serialization.SerialName("vote_count")
    val voteCount: Int? = null,
    val adult: Boolean? = null,
)
