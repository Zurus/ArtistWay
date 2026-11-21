package com.zurus.kinosvyazi.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Сетевой стек TMDB.
 *
 * Заполняется на Этапе 2 (docs/05_roadmap.md): OkHttp, Retrofit,
 * `TmdbApi`, `TmdbConfigurationApi`, перехватчики
 * (API-ключ, retry/429, таймауты) — docs/03_data_layer.md §3–§6.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule
