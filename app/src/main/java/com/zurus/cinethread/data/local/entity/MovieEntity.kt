package com.zurus.cinethread.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Фильм в Room-кэше (docs/03_data_layer.md §2, TTL — 30 дней).
 * `title` всегда безопасен (маппер подставляет fallback),
 * `originalTitle` хранится отдельно для правил маппинга.
 */
@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val movieId: Int,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val releaseDate: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val runtime: Int?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val adult: Boolean?,
    val fetchedAtMillis: Long,
)
