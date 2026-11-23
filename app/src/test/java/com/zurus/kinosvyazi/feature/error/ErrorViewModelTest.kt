package com.zurus.kinosvyazi.feature.error

import androidx.lifecycle.SavedStateHandle
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.core.ui.UiState
import com.zurus.kinosvyazi.ui.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты [ErrorViewModel] и [ErrorType]: каждому типу ошибки соответствуют
 * заголовок, текст и возможность «Повторить» (docs/01_concept.md §2.10, Этап 5).
 */
class ErrorViewModelTest {

    @Test
    fun `route maps to corresponding type`() {
        for (type in ErrorType.entries) {
            assertEquals(type, ErrorType.fromRoute(type.route))
        }
    }

    @Test
    fun `unknown or missing route falls back to UNKNOWN`() {
        assertEquals(ErrorType.UNKNOWN, ErrorType.fromRoute("garbage"))
        assertEquals(ErrorType.UNKNOWN, ErrorType.fromRoute(null))
    }

    @Test
    fun `not_found is the only type without retry`() {
        assertFalse(ErrorType.NOT_FOUND.canRetry)
        for (type in ErrorType.entries.filter { it != ErrorType.NOT_FOUND }) {
            assertTrue(type.canRetry)
        }
    }

    @Test
    fun `each type has title and text resources`() {
        val expectedTitles = mapOf(
            ErrorType.NOT_FOUND to R.string.error_title_not_found,
            ErrorType.NETWORK to R.string.error_title_network,
            ErrorType.SERVER to R.string.error_title_server,
            ErrorType.RATE_LIMIT to R.string.error_title_rate_limit,
            ErrorType.DATA_INVALID to R.string.error_title_data,
            ErrorType.GENERATION to R.string.error_title_generation,
            ErrorType.UNKNOWN to R.string.error_screen_title,
        )
        for ((type, titleId) in expectedTitles) {
            assertEquals(titleId, type.titleId)
            assertTrue("у типа $type должен быть текст", type.textId != 0)
        }
    }

    @Test
    fun `view model builds content state from route argument`() {
        val viewModel = viewModel(ErrorType.RATE_LIMIT.route)
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Content)
        val content = (state as UiState.Content).value
        assertEquals(ErrorType.RATE_LIMIT, content.type)
        assertTrue(content.canRetry)
    }

    @Test
    fun `view model falls back to UNKNOWN for missing argument`() {
        val viewModel = viewModel(null)
        val content = (viewModel.uiState.value as UiState.Content).value
        assertEquals(ErrorType.UNKNOWN, content.type)
    }

    @Test
    fun `not_found hides retry`() {
        val viewModel = viewModel(ErrorType.NOT_FOUND.route)
        val content = (viewModel.uiState.value as UiState.Content).value
        assertFalse(content.canRetry)
    }

    private fun viewModel(route: String?): ErrorViewModel =
        ErrorViewModel(
            SavedStateHandle(if (route != null) mapOf(NavRoutes.ERROR_TYPE to route) else emptyMap()),
        )
}
