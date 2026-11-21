package com.zurus.kinosvyazi.data.network

/**
 * Сетевые константы TMDB (docs/03_data_layer.md §3).
 * Базовый URL и локаль — фиксированные решения MVP.
 */
object NetworkConfig {

    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

    /** Локаль данных MVP: английский (docs/03_data_layer.md §3). */
    const val LANGUAGE = "en-US"

    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 15L
}
