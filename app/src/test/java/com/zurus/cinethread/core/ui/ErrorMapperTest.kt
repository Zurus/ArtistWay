package com.zurus.cinethread.core.ui

import com.zurus.cinethread.R
import com.zurus.cinethread.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Критерий приёмки Этапа 1 (docs/05_roadmap.md):
 * unit-тесты `ErrorMapper` для каждого `AppError` → `UiText`.
 */
class ErrorMapperTest {

    @Test
    fun mapsNetworkError() {
        assertEquals(
            UiText.Resource(R.string.error_network),
            ErrorMapper.map(AppError.Network),
        )
    }

    @Test
    fun mapsServerError() {
        assertEquals(
            UiText.Resource(R.string.error_server),
            ErrorMapper.map(AppError.Server),
        )
    }

    @Test
    fun mapsRateLimitError() {
        assertEquals(
            UiText.Resource(R.string.error_rate_limit),
            ErrorMapper.map(AppError.RateLimit(retryAfterSeconds = 60)),
        )
    }

    @Test
    fun mapsDataInvalidError() {
        assertEquals(
            UiText.Resource(R.string.error_data_invalid),
            ErrorMapper.map(AppError.DataInvalid(details = "missing person_id")),
        )
    }

    @Test
    fun mapsOfflineError() {
        assertEquals(
            UiText.Resource(R.string.error_offline),
            ErrorMapper.map(AppError.Offline),
        )
    }

    @Test
    fun mapsBudgetExceededError() {
        assertEquals(
            UiText.Resource(R.string.error_budget_exceeded),
            ErrorMapper.map(AppError.BudgetExceeded(reason = "time")),
        )
    }
}
