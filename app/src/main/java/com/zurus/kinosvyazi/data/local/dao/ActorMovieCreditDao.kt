package com.zurus.kinosvyazi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zurus.kinosvyazi.data.local.entity.ActorMovieCreditEntity

@Dao
interface ActorMovieCreditDao {

    @Query("SELECT * FROM actor_movie_credits WHERE personId = :personId")
    suspend fun getByPersonId(personId: Int): List<ActorMovieCreditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(credits: List<ActorMovieCreditEntity>)

    /** Удаляет связи, отсутствующие в свежем ответе TMDB (docs/03_data_layer.md §5). */
    @Query("DELETE FROM actor_movie_credits WHERE personId = :personId AND movieId NOT IN (:movieIds)")
    suspend fun deleteStaleLinks(personId: Int, movieIds: List<Int>)

    @Query("DELETE FROM actor_movie_credits WHERE personId = :personId")
    suspend fun deleteByPersonId(personId: Int)
}
