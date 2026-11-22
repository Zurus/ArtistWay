package com.zurus.kinosvyazi.di

import android.app.Application
import com.zurus.kinosvyazi.core.common.Clock
import com.zurus.kinosvyazi.core.common.IoDispatcher
import com.zurus.kinosvyazi.core.ui.ChainResolver
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.DataAvailabilityChecker
import com.zurus.kinosvyazi.domain.repository.GamePairRepository
import com.zurus.kinosvyazi.domain.repository.GameSessionRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.usecase.CheckDataAvailabilityUseCase
import com.zurus.kinosvyazi.domain.usecase.CheckDeadEndUseCase
import com.zurus.kinosvyazi.domain.usecase.CommitTransitionUseCase
import com.zurus.kinosvyazi.domain.usecase.FinishGameUseCase
import com.zurus.kinosvyazi.domain.usecase.GetActorMoviesUseCase
import com.zurus.kinosvyazi.domain.usecase.GetGameSessionUseCase
import com.zurus.kinosvyazi.domain.usecase.GetMovieCastUseCase
import com.zurus.kinosvyazi.domain.usecase.StartGameUseCase
import com.zurus.kinosvyazi.domain.usecase.UndoLastTransitionUseCase
import com.zurus.kinosvyazi.domain.usecase.ValidateTransitionUseCase
import com.zurus.kinosvyazi.domain.util.Budget
import com.zurus.kinosvyazi.domain.util.PairGenConfig
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
 * Этап 3: в графе появляются GamePairRepository, фабрики
 * `PairGenConfig`/`Budget` и все UseCase — их инжект ловит
 * недостающие биндинги (docs/05_roadmap.md, Этап 3).
 *
 * Локальный unit-тест на Robolectric — исполняется без устройства
 * командой `./gradlew test`.
 */@HiltAndroidTest
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

    @Inject
    lateinit var gamePairRepository: GamePairRepository

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var pairGenConfig: PairGenConfig

    @Inject
    lateinit var budgetFactory: () -> Budget

    @Inject
    lateinit var dataAvailabilityChecker: DataAvailabilityChecker

    @Inject
    lateinit var startGameUseCase: StartGameUseCase

    @Inject
    lateinit var getActorMoviesUseCase: GetActorMoviesUseCase

    @Inject
    lateinit var getMovieCastUseCase: GetMovieCastUseCase

    @Inject
    lateinit var validateTransitionUseCase: ValidateTransitionUseCase

    @Inject
    lateinit var commitTransitionUseCase: CommitTransitionUseCase

    @Inject
    lateinit var undoLastTransitionUseCase: UndoLastTransitionUseCase

    @Inject
    lateinit var finishGameUseCase: FinishGameUseCase

    @Inject
    lateinit var getGameSessionUseCase: GetGameSessionUseCase

    @Inject
    lateinit var checkDeadEndUseCase: CheckDeadEndUseCase

    @Inject
    lateinit var checkDataAvailabilityUseCase: CheckDataAvailabilityUseCase

    @Inject
    lateinit var chainResolver: ChainResolver

    @Test
    fun graphAssemblesAndApplicationIsInjected() {
        rule.inject()
        assertNotNull("Инжект Application должен пройти", app)
        assertNotNull("AppModule должен предоставлять @IoDispatcher", ioDispatcher)
        assertNotNull("RepositoryModule должен биндить ActorRepository", actorRepository)
        assertNotNull("RepositoryModule должен биндить MovieRepository", movieRepository)
        assertNotNull("RepositoryModule должен биндить GameSessionRepository", gameSessionRepository)
        assertNotNull("RepositoryModule должен биндить GamePairRepository", gamePairRepository)
        assertNotNull("AppModule должен предоставлять Clock", clock)
        assertNotNull("AppModule должен предоставлять PairGenConfig", pairGenConfig)
        assertNotNull("AppModule должен предоставлять фабрику Budget", budgetFactory)
        assertNotNull("StartGameUseCase должен инжектиться", startGameUseCase)
        assertNotNull("GetActorMoviesUseCase должен инжектиться", getActorMoviesUseCase)
        assertNotNull("GetMovieCastUseCase должен инжектиться", getMovieCastUseCase)
        assertNotNull("ValidateTransitionUseCase должен инжектиться", validateTransitionUseCase)
        assertNotNull("CommitTransitionUseCase должен инжектиться", commitTransitionUseCase)
        assertNotNull("UndoLastTransitionUseCase должен инжектиться", undoLastTransitionUseCase)
        assertNotNull("FinishGameUseCase должен инжектиться", finishGameUseCase)
        assertNotNull("GetGameSessionUseCase должен инжектиться", getGameSessionUseCase)
        assertNotNull("CheckDeadEndUseCase должен инжектиться", checkDeadEndUseCase)
        assertNotNull("RepositoryModule должен биндить DataAvailabilityChecker", dataAvailabilityChecker)
        assertNotNull("CheckDataAvailabilityUseCase должен инжектиться", checkDataAvailabilityUseCase)
        assertNotNull("ChainResolver должен инжектиться", chainResolver)
    }
}
