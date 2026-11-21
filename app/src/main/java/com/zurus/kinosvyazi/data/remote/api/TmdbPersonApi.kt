package com.zurus.kinosvyazi.data.remote.api

import com.zurus.kinosvyazi.data.remote.dto.TmdbPersonDto
import com.zurus.kinosvyazi.data.remote.dto.TmdbPersonMovieCreditsResponseDto
import com.zurus.kinosvyazi.data.remote.dto.TmdbPersonSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Эндпоинты TMDB по актёрам (docs/03_data_layer.md §3).
 *
 * Локаль фиксирована `en-US`: параметр вынесен, чтобы в будущем
 * переключить локаль без изменения DTO.
 */
interface TmdbPersonApi {

    @GET("search/person")
    suspend fun searchPerson(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = LANGUAGE,
        @Query("page") page: Int = 1,
    ): TmdbPersonSearchResponseDto

    @GET("person/{person_id}")
    suspend fun getPerson(
        @Path("person_id") personId: Int,
        @Query("language") language: String = LANGUAGE,
    ): TmdbPersonDto

    @GET("person/{person_id}/movie_credits")
    suspend fun getPersonMovieCredits(
        @Path("person_id") personId: Int,
        @Query("language") language: String = LANGUAGE,
    ): TmdbPersonMovieCreditsResponseDto

    companion object {
        const val LANGUAGE = "en-US"
    }
}
