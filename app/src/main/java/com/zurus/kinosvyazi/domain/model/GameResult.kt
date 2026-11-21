package com.zurus.kinosvyazi.domain.model

/**
 * Причина завершения партии (docs/01_concept.md, §6.19).
 */
enum class GameResultReason {
    /** Победа: достигнут целевой актёр. */
    WIN,

    /** Игрок сдался. */
    SURRENDER,

    /** Тупик: легальных ходов не осталось. */
    DEAD_END,

    /** Ошибка (например, исчерпан бюджет). */
    ERROR,

    /** Игрок начал новую партию, не завершив предыдущую. */
    NEW_GAME,
}

/**
 * Результат завершённой партии — данные для экранов результата
 * (docs/01_concept.md, §2.8–2.11).
 *
 * Выводится из партии и её цепочки (docs/03_data_layer.md, §2).
 *
 * @property reason причина завершения.
 * @property startActor стартовый актёр.
 * @property targetActor целевой актёр.
 * @property lastActor актёр, на котором закончилась цепочка.
 * @property chain полная цепочка в порядке шагов.
 * @property stepCount количество выполненных шагов.
 * @property guaranteedDistance длина гарантированного решения генератора или `null`.
 * @property startedAtMillis время начала партии (epoch millis).
 * @property finishedAtMillis время завершения партии (epoch millis).
 */
data class GameResult(
    val reason: GameResultReason,
    val startActor: Actor,
    val targetActor: Actor,
    val lastActor: Actor,
    val chain: List<ChainStep>,
    val stepCount: Int,
    val guaranteedDistance: Int?,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
) {

    /** `true`, если игрок достиг целевого актёра. */
    val isWin: Boolean
        get() = reason == GameResultReason.WIN
}
