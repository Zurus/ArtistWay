package com.zurus.cinethread.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Участник `cast` из `/movie/{movie_id}/credits` (docs/03_data_layer.md §1).
 *
 * `id` обязателен для сохранения (записи без него игнорируются,
 * docs/03_data_layer.md §4); `name`/`character`/`profilePath`
 * могут отсутствовать — их закрывает маппер.
 */
@Serializable
data class TmdbMovieCastMemberDto(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("original_name")
    val originalName: String? = null,
    val character: String? = null,
    @SerialName("credit_id")
    val creditId: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null,
    val order: Int? = null,
    val popularity: Double? = null,
    val adult: Boolean? = null,
)
