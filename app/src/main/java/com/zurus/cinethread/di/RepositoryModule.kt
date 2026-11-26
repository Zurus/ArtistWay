package com.zurus.cinethread.di

import com.zurus.cinethread.data.repository.ActorRepositoryImpl
import com.zurus.cinethread.data.repository.GamePairRepositoryImpl
import com.zurus.cinethread.data.repository.GameSessionRepositoryImpl
import com.zurus.cinethread.data.repository.MovieRepositoryImpl
import com.zurus.cinethread.data.remote.TmdbAvailabilityChecker
import com.zurus.cinethread.domain.repository.ActorRepository
import com.zurus.cinethread.domain.repository.DataAvailabilityChecker
import com.zurus.cinethread.domain.repository.GamePairRepository
import com.zurus.cinethread.domain.repository.GameSessionRepository
import com.zurus.cinethread.domain.repository.MovieRepository
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
