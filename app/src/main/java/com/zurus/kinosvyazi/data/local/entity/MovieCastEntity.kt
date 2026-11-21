package com.zurus.kinosvyazi.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Состав фильма: `person_id` + `movie_id` (docs/03_data_layer.md §2, TTL — 7 дней).
 * Только из `cast`; `crew` связи не создаёт.
 */
@Entity(
    tableName = "movie_cast",
    indices = [Index("movieId"), Index("personId")],
)
data class MovieCastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val personId: Int,
    val movieId: Int,
    val character: String?,
    val creditId: String?,
    val order: Int?,
    val fetchedAtMillis: Long,
)
