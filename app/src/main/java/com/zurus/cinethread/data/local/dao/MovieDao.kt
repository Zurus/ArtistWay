package com.zurus.cinethread.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zurus.cinethread.data.local.entity.MovieEntity

@Dao
interface MovieDao {

    @Query("SELECT * FROM movies WHERE movieId = :movieId")
    suspend fun getById(movieId: Int): MovieEntity?

    @Query("SELECT * FROM movies WHERE movieId IN (:movieIds)")
    suspend fun getByIds(movieIds: List<Int>): List<MovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<MovieEntity>)
}
