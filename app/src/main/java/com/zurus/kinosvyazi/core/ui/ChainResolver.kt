package com.zurus.kinosvyazi.core.ui

import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.model.Movie
import com.zurus.kinosvyazi.domain.repository.ActorRepository
import com.zurus.kinosvyazi.domain.repository.MovieRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Имена/изображения для отрисовки цепочки партии:
 * карты актёров и фильмов по TMDB ID (docs/01_concept.md, §2.11).
 *
 * Ошибки отдельных lookups игнорируются — UI показывает заглушки
 * вида «Актёр 12». Кэшированные данные (Room) делают поиск дешёвым.
 */
class ChainResolver @Inject constructor(
    private val actorRepository: ActorRepository,
    private val movieRepository: MovieRepository,
) {

    suspend fun resolve(session: GameSession): ChainMaps {
        val actorIds = LinkedHashSet(
            listOfNotNull(
                session.startActor.tmdbId,
                session.targetActor.tmdbId,
                session.currentActor.tmdbId,
            ),
        )
        val movieIds = LinkedHashSet<Int>()
        session.chain.forEach { step ->
            actorIds += step.fromActorId
            actorIds += step.toActorId
            movieIds += step.movieId
        }
        val actors = lookupActors(actorIds)
        val movies = lookupMovies(movieIds)
        return ChainMaps(actors, movies)
    }

    private suspend fun lookupActors(ids: Set<Int>): Map<Int, Actor> =
        coroutineScope {
            ids.map { id ->
                async {
                    when (val result = actorRepository.getActor(id)) {
                        is Result.Success -> id to result.value
                        is Result.Failure -> null
                    }
                }
            }.map { it.await() }.filterNotNull().toMap()
        }

    private suspend fun lookupMovies(ids: Set<Int>): Map<Int, Movie> =
        coroutineScope {
            ids.map { id ->
                async {
                    when (val result = movieRepository.getMovie(id)) {
                        is Result.Success -> id to result.value
                        is Result.Failure -> null
                    }
                }
            }.map { it.await() }.filterNotNull().toMap()
        }
}

/** Срез данных цепочки для [com.zurus.kinosvyazi.ui.components.ChainView]. */
data class ChainMaps(
    val actors: Map<Int, Actor>,
    val movies: Map<Int, Movie>,
)
