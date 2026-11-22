package com.zurus.kinosvyazi.feature.home

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.domain.repository.DataAvailabilityChecker
import com.zurus.kinosvyazi.domain.usecase.CheckDataAvailabilityUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Тесты [HomeViewModel]: индикатор доступности TMDB (docs/05_roadmap.md, пункт 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `tmdb available - content with available`() = runTest {
        val viewModel = createViewModel(java.util.concurrent.atomic.AtomicBoolean(true))
        viewModel.load()
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        assertTrue((state as UiState.Content).value.tmdbAvailable)
    }

    @Test
    fun `tmdb unavailable - content with unavailable`() = runTest {
        val viewModel = createViewModel(java.util.concurrent.atomic.AtomicBoolean(false))
        viewModel.load()
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        assertTrue(!(state as UiState.Content).value.tmdbAvailable)
    }

    @Test
    fun `retry rechecks availability`() = runTest {
        val available = java.util.concurrent.atomic.AtomicBoolean(false)
        val viewModel = createViewModel(available)
        viewModel.load()
        assertTrue(!(viewModel.uiState.value as UiState.Content).value.tmdbAvailable)
        available.set(true)
        viewModel.retryAvailability()
        assertTrue((viewModel.uiState.value as UiState.Content).value.tmdbAvailable)
    }

    private fun createViewModel(available: java.util.concurrent.atomic.AtomicBoolean): HomeViewModel {
        val checker = object : DataAvailabilityChecker {
            override suspend fun check(): Result<Unit, AppError> =
                if (available.get()) Result.Success(Unit) else Result.Failure(AppError.Offline)
        }
        val useCase = CheckDataAvailabilityUseCase(checker)
        return HomeViewModel(useCase, dispatcher)
    }
}
