package com.zurus.cinethread.domain.util

/**
 * Бюджет генерации/проверки (docs/04_algorithms.md §1.1, §3.6, §5.4).
 *
 * Единая точка учёта стоимости раскрытия графа:
 * - каждый реальный запрос данных (через [GenCache]) растит [requestsUsed];
 * - каждый посещённый узел графа растит [nodesVisited];
 * - wall-clock время меряется с момента создания.
 *
 * Исчерпание бюджета — **валидное состояние**, а не исключение:
 * алгоритмы возвращают `BUDGET_EXCEEDED`/`UNKNOWN`, не бросая ошибок
 * (docs/04_algorithms.md §4.5).
 *
 * Два профиля (docs/debugging.md D1):
 * - [DEFAULT_*] — строгий: справочные проверки во время партии
 *   (`CheckDeadEndUseCase`), результат advisory, геймплей не подвешивать;
 * - [GENERATION_*] — щадящий: `generatePair`. Реальный канал работает
 *   последовательно, ~1 запрос/с (логи в docs/debugging.md): 6 секунд
 *   не хватало даже на один уровень фильмографии звезды, и старт
 *   игрался в `BUDGET_EXCEEDED`.
 *
 * @property maxRequests лимит запросов данных (рекоменд. 200–400, §3.6).
 * @property maxNodesVisited лимит посещений узлов (рекоменд. 1000–2000, §3.6).
 * @property maxWallClockMs лимит времени в мс (профиль зависит от сценария, §5.4).
 * @property now источник времени (системные часы; управляемый в тестах).
 */
class Budget(
    val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    val maxNodesVisited: Int = DEFAULT_MAX_NODES_VISITED,
    val maxWallClockMs: Long = DEFAULT_MAX_WALL_CLOCK_MS,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** Сделано запросов данных; попадания в кэш не учитываются (§1.1). */
    var requestsUsed: Int = 0
        private set

    /** Посещено узлов графа. */
    var nodesVisited: Int = 0
        private set

    /** Время создания (ms epoch) для wall-clock лимита. */
    val startedAtMs: Long = now()

    /** `true`, если достигнут любой лимит (§1.1). */
    fun exhausted(): Boolean =
        requestsUsed >= maxRequests ||
            nodesVisited >= maxNodesVisited ||
            (now() - startedAtMs) >= maxWallClockMs

    /** Фиксирует запрос данных (вызывается [GenCache] при промахе кэша). */
    fun recordRequest() {
        requestsUsed += 1
    }

    /** Фиксирует посещённый узел графа (вызывает BFS за каждого актёра фронта). */
    fun recordNodeVisited() {
        nodesVisited += 1
    }

    companion object {
        /**
         * Строгий профиль: дефолтные значения (§3.6) — для справочных
         * проверок во время партии (`CheckDeadEndUseCase`).
         */
        const val DEFAULT_MAX_REQUESTS = 300

        const val DEFAULT_MAX_NODES_VISITED = 1500

        const val DEFAULT_MAX_WALL_CLOCK_MS = 6_000L

        /**
         * Щадящий профиль: лимит запросов на весь `generatePair`
         * (все пары суммарно). ~1 запрос/с => до 500 реальных запросов,
         * но wall-clock сработает раньше.
         */
        const val GENERATION_MAX_REQUESTS = 500

        /**
         * Щадящий профиль: суммарный лимит узлов формальный (BFS
         * считает узлы в per-pair бюджете внутри `generatePair`);
         * большой, чтобы не ограничивал.
         */
        const val GENERATION_MAX_NODES_VISITED = 20_000

        /**
         * Щадящий профиль: wall-clock всего `generatePair`, мс (D1).
         * 2 минуты — верхняя граница для медленного канала; успешное
         * время заметно меньше: GenCache/Room делают повторные пары
         * почти бесплатными.
         */
        const val GENERATION_MAX_WALL_CLOCK_MS = 120_000L
    }
}
