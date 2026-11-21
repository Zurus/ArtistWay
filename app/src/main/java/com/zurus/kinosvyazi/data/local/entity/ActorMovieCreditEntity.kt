package com.zurus.kinosvyazi.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Фильмография актёра: `person_id` + `movie_id` (docs/03_data_layer.md §2,
 * TTL — 7 дней). Основной источник доступных переходов.
 */
@Entity(
    tableName = "actor_movie_credits",
    indices = [Index("personId"), Index("movieId")],
)
data class ActorMovieCreditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val personId: Int,
    val movieId: Int,
    val character: String?,
    val creditId: String?,
    val fetchedAtMillis: Long,
)
