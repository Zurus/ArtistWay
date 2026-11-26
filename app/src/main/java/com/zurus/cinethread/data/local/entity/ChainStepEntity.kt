package com.zurus.cinethread.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Шаг цепочки (docs/03_data_layer.md §2): порядок — по `stepIndex`,
 * восстанавливается при загрузке партии.
 */
@Entity(
    tableName = "chain_steps",
    indices = [Index("sessionId"), Index(value = ["sessionId", "stepIndex"], unique = true)],
)
data class ChainStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val stepIndex: Int,
    val fromPersonId: Int,
    val movieId: Int,
    val toPersonId: Int,
    val createdAtMillis: Long,
)
