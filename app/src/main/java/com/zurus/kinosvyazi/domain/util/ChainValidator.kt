package com.zurus.kinosvyazi.domain.util

import com.zurus.kinosvyazi.core.common.AppError
import com.zurus.kinosvyazi.core.common.Result
import com.zurus.kinosvyazi.domain.model.GameSession

/** Причина отклонения цепочки (docs/04_algorithms.md §2.4). */
enum class ChainInvalidReason {
    /** Шаг не продолжает предыдущий (`fromActorId != cursor`). */
    CHAIN_BREAK,

    /** Актёр уже был в цепочке. */
    ACTOR_REPEAT,

    /** Фильм уже был в цепочке. */
    MOVIE_REPEAT,

    /** Актёр отсутствует в cast фильма-перехода. */
    ACTOR_NOT_IN_CAST,

    /** Неположительный ID (сломанная цепочка в хранилище). */
    INVALID_IDS,
}

/** Результат валидации цепочки (§2.4). */
sealed interface ValidationResult {

    /** Цепочка валидна: от [startActorId] до [endActorId]. */
    data class Valid(val startActorId: Int, val endActorId: Int) : ValidationResult

    /** Цепочка невалидна; [reason] и [message] — для логов/отладки. */
    data class Invalid(val reason: ChainInvalidReason, val message: String) : ValidationResult
}

/**
 * Валидация уже построенной цепочки (§2.4).
 *
 * Вызывается при загрузке сохранённой партии, перед фиксацией перехода
 * и при отладке восстановленной цепочки. Новых сетевых запросов не
 * выполняет, если `movie_cast` уже в кэше (§5).
 */
object ChainValidator {

    /**
     * Проверяет [GameSession.chain] по порядку.
     *
     * @param session партия, цепочка которой валидируется.
     * @param cache источник cast-данных (кэш/бюджет).
     * @return [ValidationResult] при успехе либо ошибка данных.
     */
    suspend fun validateChain(
        session: GameSession,
        cache: GenCache,
    ): Result<ValidationResult, AppError> {
        val startId = session.startActor.tmdbId
        if (startId <= 0 || session.targetActor.tmdbId <= 0 || session.currentActor.tmdbId <= 0) {
            return Result.success(
                ValidationResult.Invalid(ChainInvalidReason.INVALID_IDS, "Неположительный ID актёра в партии"),
            )
        }
        val steps = session.chain
        if (steps.isEmpty()) {
            return Result.success(ValidationResult.Valid(startId, startId))
        }
        val usedActors = mutableSetOf(startId)
        val usedMovies = mutableSetOf<Int>()
        var cursor = startId
        for (step in steps) {
            if (step.fromActorId <= 0 || step.movieId <= 0 || step.toActorId <= 0) {
                return Result.success(
                    ValidationResult.Invalid(
                        ChainInvalidReason.INVALID_IDS,
                        "Некорректный ID в шаге ${step.stepIndex}",
                    ),
                )
            }
            if (step.fromActorId != cursor) {
                return Result.success(
                    ValidationResult.Invalid(
                        ChainInvalidReason.CHAIN_BREAK,
                        "Разрыв цепочки: шаг ${step.stepIndex} не продолжает предыдущий",
                    ),
                )
            }
            if (step.movieId in usedMovies) {
                return Result.success(
                    ValidationResult.Invalid(
                        ChainInvalidReason.MOVIE_REPEAT,
                        "Фильм ${step.movieId} уже использован",
                    ),
                )
            }
            if (step.toActorId in usedActors) {
                return Result.success(
                    ValidationResult.Invalid(
                        ChainInvalidReason.ACTOR_REPEAT,
                        "Актёр ${step.toActorId} уже использован",
                    ),
                )
            }
            val cast = when (val loaded = cache.movieCastIds(step.movieId)) {
                is Result.Failure -> return loaded
                is Result.Success -> loaded.value
            }
            val castIds = cast
            if (cursor !in castIds) {
                return Result.success(
                    ValidationResult.Invalid(
                        ChainInvalidReason.ACTOR_NOT_IN_CAST,
                        "Текущий актёр $cursor отсутствует в cast фильма ${step.movieId}",
                    ),
                )
            }
            if (step.toActorId !in castIds) {
                return Result.success(
                    ValidationResult.Invalid(
                        ChainInvalidReason.ACTOR_NOT_IN_CAST,
                        "Следующий актёр ${step.toActorId} отсутствует в cast фильма ${step.movieId}",
                    ),
                )
            }
            usedMovies += step.movieId
            usedActors += step.toActorId
            cursor = step.toActorId
        }
        return Result.success(ValidationResult.Valid(startId, cursor))
    }
}
