package com.zurus.kinosvyazi.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Участник `crew` из `/movie/{movie_id}/credits`.
 *
 * Разбирается для валидности JSON, но в игру не попадает:
 * переход возможен только через `cast` (docs/01_concept.md §6.2).
 */
@Serializable
data class TmdbMovieCrewMemberDto(
    val id: Int? = null,
    val name: String? = null,
    val job: String? = null,
    @SerialName("profile_path")
    val profilePath: String? = null,
)
