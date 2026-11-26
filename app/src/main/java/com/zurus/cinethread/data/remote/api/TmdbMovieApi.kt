package com.zurus.cinethread.data.remote.api

import com.zurus.cinethread.data.remote.dto.TmdbMovieCreditsResponseDto
import com.zurus.cinethread.data.remote.dto.TmdbMovieDto
import com.zurus.cinethread.data.remote.dto.TmdbMovieSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Эндпоинты TMDB по фильмам (docs/03_data_layer.md §3).
 *
 * Локаль фиксирована `en-US`: параметр вынесен, чтобы в будущем
 * переключить локаль без изменения DTO.
 */
interface TmdbMovieApi {

    @GET("movie/{movie_id}")
    suspend fun getMovie(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = LANGUAGE,
    ): TmdbMovieDto

    @GET("movie/{movie_id}/credits")
    suspend fun getMovieCredits(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = LANGUAGE,
    ): TmdbMovieCreditsResponseDto

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = LANGUAGE,
        @Query("page") page: Int = 1,
    ): TmdbMovieSearchResponseDto

    companion object {
        const val LANGUAGE = "en-US"
    }
}
