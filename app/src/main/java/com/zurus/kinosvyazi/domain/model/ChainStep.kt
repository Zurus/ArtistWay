package com.zurus.kinosvyazi.domain.model

/**
 * Один шаг цепочки: `fromActorId → movieId → toActorId`.
 *
 * @property stepIndex порядковый номер шага в цепочке (с 1, уникален в рамках партии).
 * @property fromActorId TMDB ID актёра, с которого начинается шаг.
 * @property movieId TMDB ID фильма-связки.
 * @property toActorId TMDB ID актёра, на котором заканчивается шаг.
 */
data class ChainStep(
    val stepIndex: Int,
    val fromActorId: Int,
    val movieId: Int,
    val toActorId: Int,
)
