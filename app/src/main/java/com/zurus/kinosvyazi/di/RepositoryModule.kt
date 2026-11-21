package com.zurus.kinosvyazi.di

import com.zurus.kinosvyazi.data.repository.ActorRepositoryImpl
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Привязки реализаций репозиториев к доменным контрактам.
 * Срез 1 этапа 2: только [ActorRepository]; [com.zurus.kinosvyazi.domain.repository.MovieRepository]
 * и [com.zurus.kinosvyazi.domain.repository.GameSessionRepository] — срез 2.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindActorRepository(impl: ActorRepositoryImpl): ActorRepository
}
