package com.zurus.kinosvyazi.di

import com.zurus.kinosvyazi.data.repository.ActorRepositoryImpl
import com.zurus.kinosvyazi.data.repository.GamePairRepositoryImpl
import com.zurus.kinosvyazi.data.repository.GameSessionRepositoryImpl
import com.zurus.kinosvyazi.data.repository.MovieRepositoryImpl
import com.zurus.kinosvyazi.data.remote.TmdbAvailabilityChecker
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.DataAvailabilityChecker
import com.zurus.kinosvyazi.domain.repository.GamePairRepository
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Привязки реализаций репозиториев к доменным контрактам
 * (docs/05_roadmap.md, Этап 2: три репозитория data layer,
 * Этап 3: GamePairRepository).
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

    @Binds
    @Singleton
    abstract fun bindGamePairRepository(impl: GamePairRepositoryImpl): GamePairRepository

    @Binds
    @Singleton
    abstract fun bindDataAvailabilityChecker(impl: TmdbAvailabilityChecker): DataAvailabilityChecker
}
