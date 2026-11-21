package com.zurus.kinosvyazi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zurus.kinosvyazi.data.local.entity.ChainStepEntity
import com.zurus.kinosvyazi.data.local.entity.GameSessionEntity

@Dao
interface GameSessionDao {

    @Query(
        "SELECT * FROM game_sessions " +
            "WHERE status = :activeStatus " +
            "ORDER BY updatedAtMillis DESC LIMIT 1",
    )
    suspend fun getActive(activeStatus: String): GameSessionEntity?

    @Query("SELECT * FROM game_sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): GameSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: GameSessionEntity)

    @Query("DELETE FROM game_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM chain_steps WHERE sessionId = :sessionId ORDER BY stepIndex ASC")
    suspend fun getSteps(sessionId: String): List<ChainStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSteps(steps: List<ChainStepEntity>)

    @Query("DELETE FROM chain_steps WHERE sessionId = :sessionId")
    suspend fun deleteSteps(sessionId: String)
}
