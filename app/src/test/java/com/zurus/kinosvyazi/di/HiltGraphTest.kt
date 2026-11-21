package com.zurus.kinosvyazi.di

import android.app.Application
import com.zurus.kinosvyazi.core.common.IoDispatcher
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

    @Test
    fun graphAssemblesAndApplicationIsInjected() {
        rule.inject()
        assertNotNull("Инжект Application должен пройти", app)
        assertNotNull("AppModule должен предоставлять @IoDispatcher", ioDispatcher)
    }
}
