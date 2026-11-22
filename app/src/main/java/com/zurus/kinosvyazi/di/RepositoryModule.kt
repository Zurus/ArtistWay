package com.zurus.kinosvyazi.di

import com.zurus.kinosvyazi.data.repository.ActorRepositoryImpl
import com.zurus.kinosvyazi.data.repository.GameSessionRepositoryImpl
import com.zurus.kinosvyazi.data.repository.MovieRepositoryImpl
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Привязки реализаций репозиториев к доменным контрактам
 * (docs/05_roadmap.md, Этап 2: все три репозитория data layer).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindActorRepository(impl: ActorRepositoryImpl): ActorRepository

    @Binds
    @Singleton
    abstract fun bindMovieRepository(impl: MovieRepositoryImpl): MovieRepository

    @Binds
    @Singleton
    abstract fun bindGameSessionRepository(impl: GameSessionRepositoryImpl): GameSessionRepository
}
