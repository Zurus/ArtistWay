package com.zurus.kinosvyazi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Актёр в Room-кэше (docs/03_data_layer.md §2, TTL — 30 дней).
 * `profilePath` — относительный путь; URL собирается через TMDB configuration.
 */
@Entity(tableName = "actors")
data class ActorEntity(
    @PrimaryKey val personId: Int,
    val name: String,
    val profilePath: String?,
    val knownForDepartment: String?,
    val popularity: Double?,
    val fetchedAtMillis: Long,
)
