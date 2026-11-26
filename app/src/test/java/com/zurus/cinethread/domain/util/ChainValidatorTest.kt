package com.zurus.cinethread.domain.util

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.FakeActorRepository
import com.zurus.cinethread.domain.FakeGraph
import com.zurus.cinethread.domain.FakeMovieRepository
import com.zurus.cinethread.domain.sessionWith
import com.zurus.cinethread.domain.model.ChainStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * ChainValidator: все правила валидации цепочки
 * (docs/04_algorithms.md §2.4, Этап 3).
 */
class ChainValidatorTest {

    /** 1 --101-- 2 --102-- 3; фильм 201: только актёр 3. */
    private val graph = FakeGraph()
        .edge(1, 101).edge(2, 101)
        .edge(2, 102).edge(3, 102)
        .edge(3, 201)

    private fun cache(failWith: AppError? = null) =
        GenCache(
            FakeActorRepository(graph, failWith),
            FakeMovieRepository(graph, failWith),
            Budget(),
        )

    private fun step(index: Int, from: Int, movie: Int, to: Int) =
        ChainStep(index, from, movie, to)

    @Test
    fun `empty chain is valid`() = runBlocking {
        val result = ChainValidator.validateChain(sessionWith(emptyList(), currentActorId = 1), cache())
        assertEquals(ValidationResult.Valid(1, 1), result.getOrNull())
    }

    @Test
    fun `valid two-step chain`() = runBlocking {
        val chain = listOf(step(1, 1, 101, 2), step(2, 2, 102, 3))
        val result = ChainValidator.validateChain(sessionWith(chain, currentActorId = 3), cache())
        assertEquals(ValidationResult.Valid(1, 3), result.getOrNull())
    }

    @Test
    fun `chain break is rejected`() = runBlocking {
        val chain = listOf(step(1, 1, 101, 2), step(2, 5, 102, 3))
        val result = ChainValidator.validateChain(sessionWith(chain, currentActorId = 3), cache()).getOrNull()
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(ChainInvalidReason.CHAIN_BREAK, (result as ValidationResult.Invalid).reason)
    }

    @Test
    fun `movie repeat is rejected`() = runBlocking {
        val graph = FakeGraph().edge(1, 101).edge(2, 101).edge(3, 101)
        val chainCache = GenCache(FakeActorRepository(graph), FakeMovieRepository(graph), Budget())
        val chain = listOf(step(1, 1, 101, 2), step(2, 2, 101, 3))
        val result = ChainValidator.validateChain(sessionWith(chain, currentActorId = 3), chainCache).getOrNull()
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(ChainInvalidReason.MOVIE_REPEAT, (result as ValidationResult.Invalid).reason)
    }

    @Test
    fun `actor repeat is rejected`() = runBlocking {
        val graph = FakeGraph()
            .edge(1, 101).edge(2, 101)
            .edge(2, 102).edge(1, 102)
        val chainCache = GenCache(FakeActorRepository(graph), FakeMovieRepository(graph), Budget())
        val chain = listOf(step(1, 1, 101, 2), step(2, 2, 102, 1))
        val result = ChainValidator.validateChain(sessionWith(chain, currentActorId = 1), chainCache).getOrNull()
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(ChainInvalidReason.ACTOR_REPEAT, (result as ValidationResult.Invalid).reason)
    }

    @Test
    fun `from actor not in movie cast is rejected`() = runBlocking {
        // шаг 2 через фильм 201, в cast которого нет актёра 2
        val chain = listOf(step(1, 1, 101, 2), step(2, 2, 201, 3))
        val result = ChainValidator.validateChain(sessionWith(chain, currentActorId = 3), cache()).getOrNull()
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(ChainInvalidReason.ACTOR_NOT_IN_CAST, (result as ValidationResult.Invalid).reason)
    }

    @Test
    fun `to actor not in movie cast is rejected`() = runBlocking {
        // фильм 102 содержит актёров 2 и 3, но следующий шаг указывает на 9
        val chain = listOf(step(1, 1, 101, 2), step(2, 2, 102, 9))
        val result = ChainValidator.validateChain(sessionWith(chain, currentActorId = 9), cache()).getOrNull()
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(ChainInvalidReason.ACTOR_NOT_IN_CAST, (result as ValidationResult.Invalid).reason)
    }

    @Test
    fun `non positive ids are rejected`() = runBlocking {
        val chain = listOf(step(1, 1, 0, 2))
        val result = ChainValidator.validateChain(sessionWith(chain, currentActorId = 2), cache()).getOrNull()
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(ChainInvalidReason.INVALID_IDS, (result as ValidationResult.Invalid).reason)
    }

    @Test
    fun `repository failure propagates`() = runBlocking {
        val chain = listOf(step(1, 1, 101, 2))
        val result = ChainValidator.validateChain(
            sessionWith(chain, currentActorId = 2),
            cache(failWith = AppError.Network),
        )
        assertTrue(result is Result.Failure)
        assertEquals(AppError.Network, (result as Result.Failure).error)
    }
}
