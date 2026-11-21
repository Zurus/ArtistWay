package com.zurus.kinosvyazi.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Привязки доменных интерфейсов репозиториев к реализациям.
 *
 * Заполняется на Этапах 2–3 (docs/05_roadmap.md):
 * [com.zurus.kinosvyazi.domain.repository.ActorRepository],
 * [com.zurus.kinosvyazi.domain.repository.MovieRepository],
 * [com.zurus.kinosvyazi.domain.repository.GamePairRepository],
 * [com.zurus.kinosvyazi.domain.repository.GameSessionRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
