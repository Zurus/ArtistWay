package com.zurus.cinethread.di

import com.zurus.cinethread.data.mapper.GameSessionMapper
import com.zurus.cinethread.data.mapper.MovieCastMapper
import com.zurus.cinethread.data.mapper.MovieMapper
import com.zurus.cinethread.data.mapper.PersonMapper
import com.zurus.cinethread.data.mapper.PersonMovieCreditMapper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Привязки мапперов data layer (docs/03_data_layer.md §4):
 * интерфейсы → реализации `Default`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MapperModule {

    @Binds
    @Singleton
    abstract fun bindPersonMapper(impl: PersonMapper.Default): PersonMapper

    @Binds
    @Singleton
    abstract fun bindMovieMapper(impl: MovieMapper.Default): MovieMapper

    @Binds
    @Singleton
    abstract fun bindPersonMovieCreditMapper(impl: PersonMovieCreditMapper.Default): PersonMovieCreditMapper

    @Binds
    @Singleton
    abstract fun bindMovieCastMapper(impl: MovieCastMapper.Default): MovieCastMapper

    @Binds
    @Singleton
    abstract fun bindGameSessionMapper(impl: GameSessionMapper.Default): GameSessionMapper
}
