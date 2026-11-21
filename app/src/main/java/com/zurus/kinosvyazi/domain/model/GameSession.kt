package com.zurus.kinosvyazi.domain.model

/**
 * Статус игровой партии (соответствует полю `status`
 * в `game_sessions`, docs/03_data_layer.md §2).
 */
enum class GameSessionStatus {
    /** Партия идёт. */
    IN_PROGRESS,

    /** Игрок достиг целевого актёра. */
    WON,

    /** Игрок сдался. */
    SURRENDERED,

    /** Тупик: легальных ходов нет (docs/01_concept.md, §6.18). */
    DEAD_END,

    /** Ошибка (например, исчерпан бюджет). */
    ERROR,
}

/**
 * Активная или завершённая партия.
 *
 * @property startActor стартовый актёр цепочки.
 * @property targetActor целевой актёр.
 * @property currentActor актёр, на котором сейчас находится цепочка.
 * @property chain выполненные шаги по порядку (от первого к последнему).
 * @property status статус партии.
 * @property startedAtMillis время начала партии (epoch millis).
 * @property updatedAtMillis время последнего изменения.
 * @property finishedAtMillis время завершения или `null`, если партия активна.
 * @property stepCount количество выполненных шагов.
 * @property shortestPath гарантированное решение генератора или `null`.
 * @property generationSeed сид генерации или `null`.
 */
data class GameSession(
    val startActor: Actor,
    val targetActor: Actor,
    val currentActor: Actor,
    val chain: List<ChainStep>,
    val status: GameSessionStatus,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val finishedAtMillis: Long?,
    val stepCount: Int,
    val shortestPath: List<ChainStep>?,
    val generationSeed: Long?,
)
