package com.zurus.kinosvyazi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zurus.kinosvyazi.data.local.entity.ActorEntity

@Dao
interface ActorDao {

    @Query("SELECT * FROM actors WHERE personId = :personId")
    suspend fun getById(personId: Int): ActorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(actor: ActorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(actors: List<ActorEntity>)
}
