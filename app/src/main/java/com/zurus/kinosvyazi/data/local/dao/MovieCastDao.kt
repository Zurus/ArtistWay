package com.zurus.kinosvyazi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zurus.kinosvyazi.data.local.entity.MovieCastEntity

@Dao
interface MovieCastDao {

    @Query("SELECT * FROM movie_cast WHERE movieId = :movieId")
    suspend fun getByMovieId(movieId: Int): List<MovieCastEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cast: List<MovieCastEntity>)

    /** Удаляет состав, отсутствующий в свежем ответе TMDB (docs/03_data_layer.md §5). */
    @Query("DELETE FROM movie_cast WHERE movieId = :movieId AND personId NOT IN (:personIds)")
    suspend fun deleteStaleLinks(movieId: Int, personIds: List<Int>)

    @Query("DELETE FROM movie_cast WHERE movieId = :movieId")
    suspend fun deleteByMovieId(movieId: Int)
}
