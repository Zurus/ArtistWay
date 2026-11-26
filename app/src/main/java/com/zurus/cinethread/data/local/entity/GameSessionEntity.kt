package com.zurus.cinethread.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Партия (docs/03_data_layer.md §2): активная и завершённые хранятся
 * без TTL, восстанавливаются после перезапуска приложения.
 * `shortestPathJson` — сериализованный кратчайший путь (формат зафиксирован
 * на этапе реализации).
 */
@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey val sessionId: String,
    val startPersonId: Int,
    val targetPersonId: Int,
    val currentPersonId: Int,
    val status: String,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val finishedAtMillis: Long?,
    val stepCount: Int,
    val shortestPathJson: String?,
    val generationSeed: Long?,
)
