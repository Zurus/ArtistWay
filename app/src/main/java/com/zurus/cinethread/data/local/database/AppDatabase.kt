package com.zurus.cinethread.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zurus.cinethread.data.local.dao.ActorDao
import com.zurus.cinethread.data.local.dao.ActorMovieCreditDao
import com.zurus.cinethread.data.local.dao.GameSessionDao
import com.zurus.cinethread.data.local.dao.MovieCastDao
import com.zurus.cinethread.data.local.dao.MovieDao
import com.zurus.cinethread.data.local.entity.ActorEntity
import com.zurus.cinethread.data.local.entity.ActorMovieCreditEntity
import com.zurus.cinethread.data.local.entity.ChainStepEntity
import com.zurus.cinethread.data.local.entity.GameSessionEntity
import com.zurus.cinethread.data.local.entity.MovieCastEntity
import com.zurus.cinethread.data.local.entity.MovieEntity

/**
 * Room-база «КиноНить» (docs/03_data_layer.md §2).
 * Схема v1 создаётся целиком сразу: так при добавлении реализаций
 * на следующем срезе не потребуется миграция.
 * Пока все поля сущностей — примитивы/String, TypeConverters не требуются
 * (см. [Converters]).
 */
@Database(
    entities = [
        ActorEntity::class,
        MovieEntity::class,
        MovieCastEntity::class,
        ActorMovieCreditEntity::class,
        GameSessionEntity::class,
        ChainStepEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun actorDao(): ActorDao

    abstract fun movieDao(): MovieDao

    abstract fun movieCastDao(): MovieCastDao

    abstract fun actorMovieCreditDao(): ActorMovieCreditDao

    abstract fun gameSessionDao(): GameSessionDao
}
