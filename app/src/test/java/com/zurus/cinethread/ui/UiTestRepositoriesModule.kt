package com.zurus.cinethread.ui

import com.zurus.cinethread.di.RepositoryModule
import com.zurus.cinethread.domain.repository.ActorRepository
import com.zurus.cinethread.domain.repository.DataAvailabilityChecker
import com.zurus.cinethread.domain.repository.GamePairRepository
import com.zurus.cinethread.domain.repository.GameSessionRepository
import com.zurus.cinethread.domain.repository.MovieRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Подмена data layer в Compose UI-тестах (docs/05_roadmap.md, Этап 6):
 * вместо Retrofit/Room/генератора — детерминированные in-memory двойники
 * ([UiTestDoubles]). `@TestInstallIn(replaces = [RepositoryModule])` подменяет
 * `@Binds` основного [RepositoryModule] в тестовом Hilt-графе всех
 * `@HiltAndroidTest`-тестов source set (включая HiltGraphTest — он проверяет
 * только сборку графа, двойники ему подходят).
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class],
)
object UiTestRepositoriesModule {

    @Provides
    @Singleton
    fun provideActorRepository(): ActorRepository = UiTestDoubles.ActorRepo()

    @Provides
    @Singleton
    fun provideMovieRepository(): MovieRepository = UiTestDoubles.MovieRepo()

    @Provides
    @Singleton
    fun provideGamePairRepository(): GamePairRepository = UiTestDoubles.PairRepo()

    @Provides
    @Singleton
    fun provideDataAvailabilityChecker(): DataAvailabilityChecker =
        UiTestDoubles.AvailabilityChecker()

    @Provides
    @Singleton
    fun provideGameSessionRepository(): GameSessionRepository = UiTestDoubles.SessionRepo()
}
