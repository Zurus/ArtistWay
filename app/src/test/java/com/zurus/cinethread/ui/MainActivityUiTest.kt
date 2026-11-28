package com.zurus.cinethread.ui

import com.zurus.cinethread.MainActivity
import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.domain.actor
import com.zurus.cinethread.domain.model.ChainStep
import com.zurus.cinethread.domain.model.GamePair
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/**
 * Compose UI-тесты (docs/05_roadmap.md, Этап 6, пункт 2):
 * happy path до победы, отмена перехода, тупик, ошибка сети на MoviePicker.
 *
 * Робот-тесты на Robolectric (документированное отклонение от
 * instrumented-тестов — нет устройства в CI): Hilt-граф подменяет
 * [com.zurus.cinethread.di.RepositoryModule] на in-memory двойники
 * [UiTestDoubles] через [UiTestRepositoriesModule] (`@TestInstallIn(replaces)`)
 * — сценарии детерминированы, сети нет.
 *
 * Все взаимодействия — через [click]/[expectVisible]: состояние экрана
 * обновляется из `viewModelScope` (реальные `Dispatchers.IO` хопы), а
 * одиночный `waitForIdle()` не дожидается всех хопов до завершения, поэтому
 * перед каждым действием/утверждением опрашиваем дерево до появления нужной
 * ноды (таймаут [WAIT_MS] мс реального времени).
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w480dp-h960dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class MainActivityUiTest {

    @get:Rule
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        UiTestDoubles.reset()
        // Партия 1 → 3: 1 → Фильм 101 → 2 → Фильм 102 → 3.
        UiTestDoubles.current.graph.edge(1, 101).edge(2, 101).edge(2, 102).edge(3, 102)
        UiTestDoubles.current.pair = GamePair(
            startActor = actor(1),
            targetActor = actor(3),
            shortestPath = listOf(
                ChainStep(1, 1, 101, 2),
                ChainStep(2, 2, 102, 3),
            ),
            seed = 42L,
        )
        hiltRule.inject()
    }

    @Test
    fun `happy path - win screen after two transitions`() {
        // Экран запуска → «Начать игру» → выбор глубины (D5) → генерация → экран партии.
        click("Начать игру")
        click("Подобрать пару")
        expectVisible("Актёр 3")
        expectVisible("Шагов: 0")

        // Шаг 1: фильм 101 → актёр 2.
        click("Выбрать фильм")
        click("Фильм 101")
        click("Актёр 2")
        click("Подтвердить")
        expectVisible("Шагов: 1")
        // После коммита проверка тупика: цель в одном переходе (docs/04 §4.1).
        expectVisible("Цель достижима одним переходом")

        // Шаг 2: фильм 102 → актёр 3 (цель) → победа.
        click("Выбрать фильм")
        click("Фильм 102")
        clickFirst("Актёр 3")
        click("Подтвердить")

        expectVisible("Цель достигнута!")
        expectVisible("Шагов: 2")
    }

    @Test
    fun `cancel transition - chain unchanged`() {
        click("Начать игру")
        click("Подобрать пару")
        click("Выбрать фильм")
        click("Фильм 101")
        click("Актёр 2")
        click("Отменить")

        // docs/01_concept.md §2.7: отмена не фиксирует шаг.
        expectVisible("Шагов: 0")
        expectVisible("Выбрать фильм")
    }

    @Test
    fun `dead end - dialog after commit when no path to target`() {
        // Актёр 2 — только в фильме 101; цели 3 в графе нет вообще.
        val state = UiTestDoubles.current
        state.graph = com.zurus.cinethread.domain.FakeGraph().edge(1, 101).edge(2, 101)
        state.pair = GamePair(
            startActor = actor(1),
            targetActor = actor(3),
            shortestPath = listOf(ChainStep(1, 1, 101, 2)),
            seed = 7L,
        )

        click("Начать игру")
        click("Подобрать пару")
        click("Выбрать фильм")
        click("Фильм 101")
        click("Актёр 2")
        click("Подтвердить")

        // docs/04_algorithms.md §4.2: после коммита найден тупик NO_PATH.
        expectVisible("Нет доступных переходов")
        expectVisible("Пути к цели не найдено.")
    }

    @Test
    fun `movie picker - network error shows retry and keeps failing`() {
        click("Начать игру")
        click("Подобрать пару")
        expectVisible("Выбрать фильм")

        // docs/01_concept.md §6.2: сбой сети — понятный текст + «Повторить».
        UiTestDoubles.current.actorFail = AppError.Offline
        click("Выбрать фильм")
        expectVisible("Не удалось загрузить фильмы актёра")
        expectVisible("Нет соединения с интернетом. Доступны только сохранённые данные.")

        click("Повторить")
        expectVisible("Не удалось загрузить фильмы актёра")
    }

    // --- Хелперы: ожидание асинхронного состояния + действие ---------------

    private fun click(text: String) {
        waitForText(text)
        // Кнопки лежат в verticalScroll-колонке: после первого шага цепочки
        // контент выше вьюпорта, и клик по «невидимой» ноде не доходит до онкликера.
        // performScrollTo падает на нодах без scroll-родителя — игнорируем.
        val node = composeRule.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun clickFirst(text: String) {
        waitForText(text)
        val node = composeRule.onAllNodesWithText(text).onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun expectVisible(text: String) {
        waitForText(text)
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntilAtLeastOneExists(hasText(text), WAIT_MS)
    }
}

private const val WAIT_MS: Long = 15_000
