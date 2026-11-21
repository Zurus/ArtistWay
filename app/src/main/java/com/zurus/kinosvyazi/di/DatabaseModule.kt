package com.zurus.kinosvyazi.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Room-базы: `AppDatabase`, DAO, миграции.
 *
 * Заполняется на Этапе 2 (docs/05_roadmap.md) — docs/03_data_layer.md §2, §8.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule
