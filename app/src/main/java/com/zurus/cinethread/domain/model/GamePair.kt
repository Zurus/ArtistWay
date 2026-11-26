package com.zurus.cinethread.domain.model

/**
 * Сгенерированная стартово-целевая пара партии.
 *
 * Инвариант: [shortestPath] существует и его длина не превышает
 * `maxSteps` из конфигурации генератора (docs/04_algorithms.md §2, §3.4) —
 * партия гарантированно решаемая.
 *
 * @property startActor актёр, с которого начинается цепочка.
 * @property targetActor актёр, которого нужно достичь.
 * @property shortestPath кратчайший путь, найденный генератором (гарантированное решение).
 * @property seed сид воспроизводимой генерации или `null`.
 */
data class GamePair(
    val startActor: Actor,
    val targetActor: Actor,
    val shortestPath: List<ChainStep>,
    val seed: Long?,
)
