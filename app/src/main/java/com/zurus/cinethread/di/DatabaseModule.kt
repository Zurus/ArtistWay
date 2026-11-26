package com.zurus.cinethread.di

import android.content.Context
import androidx.room.Room
import com.zurus.cinethread.data.local.dao.ActorDao
import com.zurus.cinethread.data.local.dao.ActorMovieCreditDao
import com.zurus.cinethread.data.local.dao.GameSessionDao
import com.zurus.cinethread.data.local.dao.MovieCastDao
import com.zurus.cinethread.data.local.dao.MovieDao
import com.zurus.cinethread.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room-база и DAO (docs/03_data_layer.md §2).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            // MVP: миграции не сохраняются, схема меняется деструктивно.
            // С версии 2 появятся реальные Migration-объекты.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideActorDao(database: AppDatabase): ActorDao = database.actorDao()

    @Provides
    fun provideMovieDao(database: AppDatabase): MovieDao = database.movieDao()

    @Provides
    fun provideMovieCastDao(database: AppDatabase): MovieCastDao = database.movieCastDao()

    @Provides
    fun provideActorMovieCreditDao(database: AppDatabase): ActorMovieCreditDao = database.actorMovieCreditDao()

    @Provides
    fun provideGameSessionDao(database: AppDatabase): GameSessionDao = database.gameSessionDao()

    private const val DATABASE_NAME = "cinethread.db"
}
