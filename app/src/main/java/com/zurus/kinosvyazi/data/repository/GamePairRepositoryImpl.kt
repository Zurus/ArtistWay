package com.zurus.kinosvyazi.data.repository

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.GamePair
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.GamePairRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import com.zurus.kinosvyazi.domain.repository.PairGenerationError
import com.zurus.kinosvyazi.domain.util.Budget
import com.zurus.kinosvyazi.domain.util.PairFailReason
import com.zurus.kinosvyazi.domain.util.PairGenConfig
import com.zurus.kinosvyazi.domain.util.PairGenerator
import com.zurus.kinosvyazi.domain.util.PairResult
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ThreadLocalRandom

/**
 * Реализация [GamePairRepository] (docs/04_algorithms.md §3,
 * docs/05_roadmap.md, Этап 3).
 *
 * `PairGenerator` работает с ID-графом; здесь результат доводится
 * до доменной [GamePair] (объекты [com.zurus.kinosvyazi.domain.model.Actor]
 * берутся из репозитория — обычно из кэша).
 *
 * MVP: стартовый пул — фиксированный список популярных актёров.
 * docs/04_algorithms.md §7 оставляет источник `startPool` за
 * базовым алгоритмом: позже это «popular»-кэш Room. Нерешаемые ID
 * в пуле отбраковываются генератором штатно (у них нет фильмов).
 */
@Singleton
class GamePairRepositoryImpl @Inject constructor(
    private val actorRepository: ActorRepository,
    private val movieRepository: MovieRepository,
    private val pairGenConfig: PairGenConfig,
    private val budgetFactory: () -> Budget,
) : GamePairRepository {

    override suspend fun generatePair(seed: Long?): Result<GamePair, PairGenerationError> {
        val effectiveSeed = seed ?: ThreadLocalRandom.current().nextLong()
        val budget = budgetFactory()
        val generator = PairGenerator(actorRepository, movieRepository)
        return when (
            val outcome = generator.generatePair(effectiveSeed, pairGenConfig, START_ACTOR_POOL, budget)
        ) {
            is Result.Failure -> Result.failure(PairGenerationError.DataError(outcome.error))
            is Result.Success -> when (val pair = outcome.value) {
                is PairResult.Ok -> pair.toDomain(effectiveSeed)
                is PairResult.Fail -> when (pair.reason) {
                    PairFailReason.BUDGET_EXCEEDED -> Result.failure(
                        PairGenerationError.BudgetExceeded(
                            "requests=${budget.requestsUsed}/${budget.maxRequests}, " +
                                "nodes=${budget.nodesVisited}/${budget.maxNodesVisited}",
                        ),
                    )
                    PairFailReason.NO_START_CANDIDATES,
                    PairFailReason.NO_VALID_PAIR,
                    -> Result.failure(PairGenerationError.NoValidPair)
                }
            }
        }
    }

    private suspend fun PairResult.Ok.toDomain(seed: Long): Result<GamePair, PairGenerationError> {
        val start = when (val loaded = actorRepository.getActor(startActorId)) {
            is Result.Failure -> return Result.failure(PairGenerationError.DataError(loaded.error))
            is Result.Success -> loaded.value
        }
        val target = when (val loaded = actorRepository.getActor(targetActorId)) {
            is Result.Failure -> return Result.failure(PairGenerationError.DataError(loaded.error))
            is Result.Success -> loaded.value
        }
        return Result.Success(
            GamePair(
                startActor = start,
                targetActor = target,
                shortestPath = shortestPath,
                seed = seed,
            ),
        )
    }

    /**
     * Фиксированный пул популярных актёров (MVP, docs/04 §7).
     * TMDB `person_id`; все — кастовые звёзды с большим пересечением
     * составов, что гарантирует достижимость в пределах `maxSteps`.
     */
    private companion object {
        val START_ACTOR_POOL: List<Int> = listOf(
            31, // Tom Hanks
            38, // Harrison Ford
            39, // Robin Williams
            42, // Michael Douglas
            122, // Robert De Niro
            287, // Brad Pitt
            385, // Johnny Depp
            5381, // Meryl Streep
            9226, // Tom Cruise
            11464, // Christian Bale
            1972, // Morgan Freeman
            2518, // Leonardo DiCaprio
            2871, // Robert Downey Jr.
        )
    }
}
