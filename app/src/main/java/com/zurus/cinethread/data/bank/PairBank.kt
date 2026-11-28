package com.zurus.cinethread.data.bank

import kotlin.random.Random

/**
 * Пара из банка (D4/D7, docs/debugging.md): только ID, без ключей.
 *
 * [path] — цепочка из `2 * depth - 1` ID «фильм, актёр, фильм, …»:
 * d=2: `[fA, X, fB]`; d=3: `[f1, X, f2, Y, f3]` (контракт D4,
 * `docs/pair_sources.md` §2). Концы цепочки — сами актёры [startId]/
 * [targetId], они в [path] не дублируются.
 */
data class BankPair(
    val startId: Int,
    val targetId: Int,
    val depth: Int,
    val path: List<Int>,
)

/**
 * Разобранный банк пар: индекс пар по глубине + имена актёров пула.
 *
 * [EMPTY] — банк недоступен (файла нет/бит/выключен): все проверки
 * глубины ложны, генерация уходит в онлайн (fallback, без краша).
 */
data class PairBank(
    val version: Int,
    val names: Map<Int, String>,
    val pairsByDepth: Map<Int, List<BankPair>>,
) {
    /** Нет ни одной пары на любой глубине. */
    val isEmpty: Boolean
        get() = pairsByDepth.values.all { it.isEmpty() }

    /** Покрывает ли банк глубину (есть хотя бы одна такая пара). */
    fun coversDepth(depth: Int): Boolean {
        val pairs = pairsByDepth[depth]
        return pairs != null && pairs.isNotEmpty()
    }

    /**
     * Случайная пара глубины по [rng] (детерминированно по seed)
     * либо `null`, если глубина не покрыта. Пустой список не
     * потребляет поток [rng] — seed при fallback совпадает с online.
     */
    fun random(depth: Int, rng: Random): BankPair? {
        val pairs = pairsByDepth[depth] ?: return null
        if (pairs.isEmpty()) return null
        return pairs[rng.nextInt(pairs.size)]
    }

    companion object {
        val EMPTY = PairBank(version = 0, names = emptyMap(), pairsByDepth = emptyMap())
    }
}
