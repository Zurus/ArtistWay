package com.zurus.kinosvyazi.domain.model

/**
 * Факт совместного участия [actor] в [movie].
 *
 * Единственное легальное перемещение в игре (docs/01_concept.md, §6.1–6.4):
 * переход возможен только через фильм, в котором сняты оба актёра.
 *
 * @property movie фильм, связывающий актёров.
 * @property actor актёр из этого фильма.
 * @property character имя роли или `null`, если роль без указания.
 */
data class MovieCredit(
    val movie: Movie,
    val actor: Actor,
    val character: String?,
)
