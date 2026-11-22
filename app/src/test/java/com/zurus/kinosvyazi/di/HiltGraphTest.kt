package com.zurus.kinosvyazi.di

import android.app.Application
import com.zurus.kinosvyazi.core.common.IoDispatcher
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Критерий приёмки Этапа 1 (docs/05_roadmap.md):
 * Hilt-граф собирается, `@HiltAndroidTest` инжектит `Application`.
 * Срез 2 Этапа 2: в графе появляются все три репозитория data layer
 * (Retrofit, Room, мапперы) — их инжект ловит недостающие биндинги.
 *
 * Локальный unit-тест на Robolectric — исполняется без устройства
 * командой `./gradlew test`.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class HiltGraphTest {

    @get:Rule
    val rule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var app: Application

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var actorRepository: ActorRepository

    @Inject
    lateinit var movieRepository: MovieRepository

    @Inject
    lateinit var gameSessionRepository: GameSessionRepository

    @Test
    fun graphAssemblesAndApplicationIsInjected() {
        rule.inject()
        assertNotNull("Инжект Application должен пройти", app)
        assertNotNull("AppModule должен предоставлять @IoDispatcher", ioDispatcher)
        assertNotNull("RepositoryModule должен биндить ActorRepository", actorRepository)
        assertNotNull("RepositoryModule должен биндить MovieRepository", movieRepository)
        assertNotNull("RepositoryModule должен биндить GameSessionRepository", gameSessionRepository)
    }
}
