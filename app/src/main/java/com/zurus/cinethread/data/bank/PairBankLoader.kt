package com.zurus.cinethread.data.bank

import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * Д7/D8 (docs/debugging.md): загрузчик банка пар
 * `assets/pairs_bank_v1.json` (контракт — D4, `docs/pair_sources.md` §2).
 *
 * Ассет разбирается **один раз** за процесс; далее — из памяти.
 * Файл отсутствует/бит (или банк выключен) → [PairBank.EMPTY] + `Log.w` →
 * `auto`/онлайн-режимы сами уходят в онлайн (без краша и без ошибки
 * для игрока), `bank`-режим отдаёт NoValidPair (строгий контракт D8).
 *
 * [enabled] — с D8 всегда `true` в DI: режим выбирает `BuildConfig.PAIR_SOURCE`
 * (стратегия [com.zurus.cinethread.domain.repository.PairSource]); параметр
 * оставлен для тестов. [assetReader] — шов для unit-тестов
 * (в проде — `AssetManager::open`).
 */
class PairBankLoader(
    private val enabled: Boolean,
    private val assetReader: AssetReader,
) {

    @Volatile
    private var cached: PairBank? = null

    /** Банк (кэшируется после первого разбора). */
    fun load(): PairBank {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: loadInternal().also { cached = it }
        }
    }

    private fun loadInternal(): PairBank {
        if (!enabled) return PairBank.EMPTY
        return runCatching { parse(assetReader.open(ASSET_FILE_NAME)) }
            .onFailure { t ->
                Log.w(TAG, "pair bank $ASSET_FILE_NAME unavailable, fallback to online: $t")
            }
            .getOrDefault(PairBank.EMPTY)
    }

    private fun parse(stream: InputStream): PairBank = stream.use {
        val file = json.decodeFromString<BankFile>(it.readBytes().decodeToString())
        val names = file.names
            .mapNotNull { (id, name) -> id.toIntOrNull()?.let { it to name } }
            .toMap()
        val pairsByDepth = file.pairs
            .mapNotNull { entry ->
                if (entry.s == entry.t || entry.d !in SUPPORTED_DEPTHS ||
                    entry.p.size != 2 * entry.d - 1
                ) {
                    null
                } else {
                    BankPair(entry.s, entry.t, entry.d, entry.p)
                }
            }
            .groupBy { it.depth }
        PairBank(version = file.version, names = names, pairsByDepth = pairsByDepth)
    }

    private companion object {
        const val TAG = "PairBankLoader"
        const val ASSET_FILE_NAME = "pairs_bank_v1.json"

        /** Поддерживаемые глубины игры (D5: 2..4). */
        val SUPPORTED_DEPTHS = 2..4

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    @Serializable
    private data class BankFile(
        val version: Int = 0,
        val pool: List<Int> = emptyList(),
        val names: Map<String, String> = emptyMap(),
        val pairs: List<BankEntry> = emptyList(),
    )

    @Serializable
    private data class BankEntry(
        val s: Int,
        val t: Int,
        val d: Int,
        val p: List<Int>,
    )
}

/** Источник ассета (в проде — `AssetManager::open`, в тестах — файл/байты). */
fun interface AssetReader {
    fun open(name: String): InputStream
}
