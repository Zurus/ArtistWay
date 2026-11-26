package com.zurus.cinethread.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Ответ `/movie/{movie_id}/credits` (docs/03_data_layer.md §1).
 *
 * Пустой или отсутствующий `cast` — валидное состояние
 * (docs/01_concept.md §6.3–6.4): репозиторий вернёт `Success(emptyList())`.
 */
@Serializable
data class TmdbMovieCreditsResponseDto(
    val id: Int? = null,
    val title: String? = null,
    val cast: List<TmdbMovieCastMemberDto>? = null,
    val crew: List<TmdbMovieCrewMemberDto>? = null,
)
