package com.zurus.cinethread.domain.usecase

import com.zurus.cinethread.core.common.AppError
import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.FakeActorRepository
import com.zurus.cinethread.domain.FakeGraph
import com.zurus.cinethread.domain.FakeMovieRepository
import com.zurus.cinethread.domain.sessionWith
import com.zurus.cinethread.domain.util.Budget
import com.zurus.cinethread.domain.util.PairGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * CheckDeadEndUseCase: локальные проверки и BFS-тупик (docs/04_algorithms.md §4).
 */
class CheckDeadEndUseCaseTest {

    /** 1 -101- 2 -102- 3. */
    private val graph = FakeGraph()
        .edge(1, 101).edge(2, 101)
        .edge(2, 102).edge(3, 102)

    private fun useCase(
        graph: FakeGraph = this.graph,
        config: PairGenConfig = PairGenConfig(deadEndSearchDepth = 4),
        actorFail: AppError? = null,
        budgetFactory: () -> Budget = { Budget() },
    ) = CheckDeadEndUseCase(
        FakeActorRepository(graph, failWith = actorFail),
        FakeMovieRepository(graph),
        config,
        budgetFactory,
    )

    @Test
    fun `path to target gives Ok with shortest remaining`() = runBlocking {
        val status = useCase()(sessionWith(emptyList(), currentActorId = 1))
        assertEquals(DeadEndStatus.Ok(shortestRemaining = 2), status)
    }

    @Test
    fun `actor without movies is dead end`() = runBlocking {
        val movieRepository = FakeMovieRepository(graph)
        val useCase = CheckDeadEndUseCase(
            FakeActorRepository(graph),
            movieRepository,
            PairGenConfig(deadEndSearchDepth = 4),
            { Budget() },
        )
        // актёр 9 не участвует ни в одном фильме
        val status = useCase(sessionWith(emptyList(), currentActorId = 9))
        assertEquals(DeadEndStatus.DeadEnd(DeadEndCause.NO_MOVIES), status)
        assertEquals(0, movieRepository.castCalls, "локальная проверка раньше BFS")
    }

    @Test
    fun `actor with only solo movies is dead end`() = runBlocking {
        val solo = FakeGraph().edge(1, 101)
        val status = useCase(graph = solo)(sessionWith(emptyList(), currentActorId = 1))
        assertEquals(DeadEndStatus.DeadEnd(DeadEndCause.NO_OTHER_ACTORS), status)
    }

    @Test
    fun `disconnected target gives NO_PATH`() = runBlocking {
        val disconnected = FakeGraph()
            .edge(1, 101).edge(2, 101)
            .edge(8, 201).edge(9, 201)
        val useCase = CheckDeadEndUseCase(
            FakeActorRepository(disconnected),
            FakeMovieRepository(disconnected),
            PairGenConfig(deadEndSearchDepth = 8),
            { Budget() },
        )
        val status = useCase(
            com.zurus.cinethread.domain.sessionBetween(1, 8, emptyList()),
        )
        assertEquals(DeadEndStatus.DeadEnd(DeadEndCause.NO_PATH), status)
    }

    @Test
    fun `budget exhausted gives Unknown`() = runBlocking {
        val useCase = useCase(budgetFactory = { Budget(maxRequests = 2) })
        val status = useCase(sessionWith(emptyList(), currentActorId = 1))
        assertEquals(DeadEndStatus.Unknown, status)
    }

    @Test
    fun `data failure gives Unknown`() = runBlocking {
        val status = useCase(actorFail = AppError.Network)(sessionWith(emptyList(), currentActorId = 1))
        assertEquals(DeadEndStatus.Unknown, status)
    }

    @Test
    fun `unknown is not an exception`() = runBlocking {
        // Убедительная проверка контракта §4.5: тупик и Unknown — значения,
        // а не ошибки; invoke не бросает исключения при исчерпанном бюджете.
        val useCase = useCase(budgetFactory = { Budget(maxRequests = 0) })
        val status = useCase(sessionWith(emptyList(), currentActorId = 1))
        assertTrue(status is DeadEndStatus)
    }
}
