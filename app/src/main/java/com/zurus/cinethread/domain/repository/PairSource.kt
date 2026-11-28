package com.zurus.cinethread.domain.repository

import com.zurus.cinethread.core.common.Result
import com.zurus.cinethread.domain.model.GamePair
import com.zurus.cinethread.domain.util.PairGenConfig

/**
 * D8 (docs/debugging.md): источник пары для партии — стратегия.
 *
 * Абстракция над «откуда берём GamePair»:
 * - [OnlinePairSource-совместимый] онлайн-поиск (BFS/д=2, D1–D5);
 * - банк (D4/D7, 0 запросов);
 * - stub удалённого микросервиса (P3);
 * - auto: банк покрывает глубину — из банка, иначе онлайн.
 *
 * Выбор стратегии — по `BuildConfig.PAIR_SOURCE` (local.properties →
 * `pairSource`, дефолт `auto`); неизвестное значение → `auto` + warning в лог.
 */
interface PairSource {

    /**
     * Берёт пару для партии.
     *
     * @param config конфигурация точной глубины
     *   (`PairGenConfig.forDepth(depth)`: `minSteps = maxSteps = depth`);
     *   глубина берётся из `config.maxSteps`.
     * @param seed nullable-сид (null — сервер/рандом, D3).
     * @return пара с гарантированным решением; [PairGenerationError] —
     *   валидное состояние (не исключение).
     */
    suspend fun pick(config: PairGenConfig, seed: Long?): Result<GamePair, PairGenerationError>
}
