package com.zurus.kinosvyazi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Ответ `/person/{person_id}/movie_credits` (docs/03_data_layer.md §1).
 *
 * `cast` — фильмы, в которых актёр сыграл роль (источник переходов);
 * `crew` не создаёт актёрских связей и для MVP не читается.
 */
@Serializable
data class TmdbPersonMovieCreditsResponseDto(
    val id: Int? = null,
    val cast: List<TmdbPersonMovieCreditDto>? = null,
    val crew: List<TmdbPersonMovieCreditDto>? = null,
)
