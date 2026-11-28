package com.zurus.cinethread.data.pairsource

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.repository.PairGenerationError
import com.zurus.cinethread.domain.util.PairGenConfig
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * D8 (docs/debugging.md): RemotePairSource — stub (P3):
 * понятная [PairGenerationError.DataError] вместо падения
 * (NetworkUnavailable/Timeout), контракт `pick` зафиксирован.
 */
class RemotePairSourceTest {

    @Test
    fun `stub returns clear data error without crashing`() = runTest {
        val result = RemotePairSource().pick(PairGenConfig().forDepth(2), seed = 1L)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is PairGenerationError.DataError, "ожидается DataError, получен $error")
        val appError = (error as PairGenerationError.DataError).error
        assertTrue(appError is AppError.DataInvalid, "причина — DataInvalid, а не $appError")
        assertTrue(
            appError.debugMessage.contains("microservice"),
            "сообщение должно объяснять причину: ${appError.debugMessage}",
        )
    }
}
