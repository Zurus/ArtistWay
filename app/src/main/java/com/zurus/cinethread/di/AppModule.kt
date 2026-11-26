package com.zurus.cinethread.di

import com.zurus.cinethread.core.common.Clock
import com.zurus.cinethread.core.common.DefaultDispatcher
import com.zurus.cinethread.core.common.GenerationBudget
import com.zurus.cinethread.core.common.IoDispatcher
import com.zurus.cinethread.core.common.MainDispatcher
import com.zurus.cinethread.data.time.Clock as DataClock
import com.zurus.cinethread.domain.util.Budget
import com.zurus.cinethread.domain.util.PairGenConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Базовые провайдеры приложения.
 *
 * Этап 3: фабрики `PairGenConfig`/`Budget` для UseCase и доменных
 * алгоритмов (docs/05_roadmap.md, Этап 3).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideDataClock(): DataClock = DataClock {
        System.currentTimeMillis()
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock {
        System.currentTimeMillis()
    }

    @Provides
    @Singleton
    fun providePairGenConfig(): PairGenConfig = PairGenConfig()

    /**
     * Фабрика: каждый вызов — свежий (мутабельный) [Budget] со строгим
     * дефолтным профилем — справочные проверки во время партии
     * (`CheckDeadEndUseCase`) не должны подвешивать геймплей.
     */
    @Provides
    @Singleton
    fun provideBudgetFactory(): () -> Budget = {
        Budget()
    }

    /**
     * Фабрика бюджета генерации пары (docs/debugging.md D1): щадящий
     * профиль [Budget.GENERATION_*] — реальный канал ~1 запрос/с,
     * 6-секундного wall-clock не хватало даже на один уровень
     * фильмографии звезды.
     */
    @Provides
    @Singleton
    @GenerationBudget
    fun provideGenerationBudgetFactory(): () -> Budget = {
        Budget(
            maxRequests = Budget.GENERATION_MAX_REQUESTS,
            maxNodesVisited = Budget.GENERATION_MAX_NODES_VISITED,
            maxWallClockMs = Budget.GENERATION_MAX_WALL_CLOCK_MS,
        )
    }
}
