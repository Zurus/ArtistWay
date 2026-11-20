# docs/04_algorithms.md

# Алгоритмы «КиноСвязи»

Документ описывает игровые алгоритмы доменного слоя: проверку существования пути, генерацию решаемой пары, обработку тупиков и сетевые оптимизации. Всё описано псевдокодом и не привязано к Android, Compose, Room или Retrofit.

Алгоритмы опираются только на два источника данных (см. `docs/02_architecture.md`, `docs/03_data_layer.md`):

```text
getActorMovieIds(personId) -> List<MovieId>   // фильмы, где актёр в cast
getMovieCastIds(movieId)   -> List<PersonId>  // актёры, снимавшиеся в фильме
```

Все обращения к этим источникам идут через `ActorRepository` / `MovieRepository` и учитываются в общем бюджете запросов.

---

## 1. Модель графа

Игра работает с неявным двудольным графом:

```text
        Actor ── Movie ── Actor
          │        │        │
          └────────┴────────┘
```

- Узел `Actor` — TMDB `person_id`.
- Узел `Movie` — TMDB `movie_id`.
- Ребро `Actor ↔ Movie` существует, если актёр указан в `cast` этого фильма.
- Ребро `Movie ↔ Actor` симметрично.

Переход игрока — это путь длины 2 в этом графе:

```text
A → M → B   (два ребра, один «шаг» игрока)
```

Длина пути в шагах игрока = `число рёбер / 2`.

Граф **не хранится целиком**: он раскрывается лениво по запросу. Это ключевое ограничение, которое определяет все оптимизации ниже.

### 1.1. Бюджет запросов

Все алгоритмы получают объект `Budget`:

```text
struct Budget {
    maxRequests: Int
    maxNodesVisited: Int
    maxWallClockMs: Long
    requestsUsed: Int
    nodesVisited: Int
    startedAtMs: Long

    fun exhausted(): Boolean =
        requestsUsed >= maxRequests
        or nodesVisited >= maxNodesVisited
        or (now() - startedAtMs) >= maxWallClockMs
}
```

Каждый вызов `getActorMovieIds` и `getMovieCastIds` инкрементирует `requestsUsed`. Каждый посещённый узел графа инкрементирует `nodesVisited`. Источники данных берут данные из кэша, если он актуален, — тогда `requestsUsed` не растёт (см. §5).

---

## 2. BFS для проверки пути

Используется в двух задачах:

1. **Валидация пары при генерации** — убедиться, что целевой актёр достижим и на какой глубине.
2. **Проверка тупика во время партии** — есть ли путь от текущего актёра к цели за оставшиеся шаги.

Обе задачи — это **ограниченный BFS** по актёрам, где каждый шаг раскрывает актёра в множество соседних актёров через фильмы.

### 2.1. Псевдокод

```text
struct ParentLink {
    movieId: MovieId       // фильм-переход
    prevActorId: PersonId  // откуда пришли
}

struct BfsResult {
    found: Bool
    distance: Int                     // в шагах игрока
    parents: Map<PersonId, ParentLink> // для восстановления пути
    reason: enum { FOUND, NOT_FOUND, BUDGET_EXCEEDED }
}

function bfsShortestPath(
    startId: PersonId,
    targetId: PersonId,
    maxSteps: Int,
    budget: Budget
): BfsResult

    if startId == targetId:
        return BfsResult(found=true, distance=0, parents={}, reason=FOUND)

    visited  = { startId }
    frontier = [ startId ]
    parents  = {}
    depth    = 0

    while depth < maxSteps and frontier is not empty:

        if budget.exhausted():
            return BfsResult(found=false, reason=BUDGET_EXCEEDED)

        nextFrontier = []

        for actorId in frontier:
            budget.nodesVisited += 1

            movieIds = getActorMovieIds(actorId)   // cached / TMDB
            if movieIds is empty:
                continue                            // тупик по актёру, но не терминальный

            for movieId in movieIds:
                castIds = getMovieCastIds(movieId)  // cached / TMDB
                if castIds is empty:
                    continue

                for nextId in castIds:
                    if nextId == actorId:   continue
                    if nextId in visited:   continue

                    visited.add(nextId)
                    parents[nextId] = ParentLink(movieId, actorId)

                    if nextId == targetId:
                        return BfsResult(
                            found=true,
                            distance=depth + 1,
                            parents=parents,
                            reason=FOUND
                        )

                    nextFrontier.add(nextId)

        frontier = nextFrontier
        depth += 1

    return BfsResult(found=false, reason=NOT_FOUND)
```

### 2.2. Восстановление пути

```text
function reconstructChain(
    startId: PersonId,
    targetId: PersonId,
    parents: Map<PersonId, ParentLink>
): List<ChainStep>

    // идём от цели назад, собираем шаги, затем разворачиваем
    steps = []
    cursor = targetId

    while cursor != startId:
        link = parents[cursor]              // должен существовать
        steps.push(ChainStep(
            fromActorId = link.prevActorId,
            movieId     = link.movieId,
            toActorId   = cursor
        ))
        cursor = link.prevActorId

    return steps.reverse()
```

### 2.3. Свойства и сложность

- `distance` — это **кратчайшее** расстояние по числу шагов. Это важно для генерации: BFS даёт фактическую длину пути, по которой можно фильтровать «слишком простые» пары.
- Временная сложность в худшем случае — `O(V + E)` по раскрытой части графа, но на практике ограничена `budget.maxNodesVisited` и `budget.maxRequests`.
- BFS останавливается на первом попадании в `targetId` — это гарантирует минимальное расстояние.

### 2.4. Проверка пути игрока (ChainValidator)

Помимо BFS, домен валидирует уже **построенную** цепочку независимо от кэша:

```text
function validateChain(session: GameSession, steps: List<ChainStep>): ValidationResult

    if steps.isEmpty():
        return Valid(session.startActorId, session.startActorId)

    usedActors = { session.startActorId }
    usedMovies = {}

    cursor = session.startActorId
    for step in steps:
        if step.fromActorId != cursor:
            return Invalid("Разрыв цепочки: шаг не продолжает предыдущий")

        if step.movieId in usedMovies:
            return Invalid("Фильм уже использован")
        if step.toActorId in usedActors:
            return Invalid("Актёр уже использован")

        castIds = getMovieCastIds(step.movieId)          // кэш обязателен
        if cursor    not in castIds: return Invalid("Текущий актёр отсутствует в cast")
        if step.toActorId not in castIds: return Invalid("Следующий актёр отсутствует в cast")

        usedMovies.add(step.movieId)
        usedActors.add(step.toActorId)
        cursor = step.toActorId

    return Valid(session.startActorId, cursor)
```

`validateChain` вызывается:

- при загрузке сохранённой партии из Room (после восстановления из `game_sessions` + `chain_steps`);
- перед фиксацией нового перехода;
- при отладке восстановленной цепочки по `generationSeed`.

Валидация **не выполняет новых сетевых запросов**, если `movie_cast` уже в Room (см. §5).

---

## 3. Генерация валидной пары

Задача: получить пару `(startActor, targetActor)` такую, что:

1. `startActor ≠ targetActor`.
2. Существует путь `startActor → … → targetActor` длиной `d` шагов, где `d ≤ config.maxSteps`.
3. Желательно `d ≥ config.minSteps` (иначе пара тривиальна).
4. Пара воспроизводима по `seed`.
5. Генератор укладывается в `budget`.

Генератор работает по принципу **BFS от стартового актёра + выбор цели из достигнутых слоёв**. Это гарантирует существование пути по построению: цель берётся из уже раскрытого BFS-дерева.

### 3.1. Конфигурация генерации

```text
struct PairGenConfig {
    minSteps: Int            // например, 2
    preferredMinSteps: Int   // например, 3
    preferredMaxSteps: Int   // например, 5
    maxSteps: Int            // например, 6..8
    startPoolSize: Int       // сколько стартовых актёров перебрать
    maxFrontierPerActor: Int // ограничение раскрытия (top-K)
    targetSampleSize: Int    // сколько целей пробовать на одном старте
    allowTrivial: Bool       // разрешать пары с d=1
}
```

### 3.2. Псевдокод

```text
function generatePair(seed: Long, config: PairGenConfig, budget: Budget): PairResult

    rng = Random(seed)
    usedActors = {}

    // 1. Берём пул стартовых актёров (из "popular" кэша Room или из заранее заданного списка)
    startPool = loadStartActorPool(config.startPoolSize, rng)
    if startPool is empty:
        return PairResult.fail(NO_START_CANDIDATES)

    shuffle(startPool, rng)

    // 2. Пробуем стартовых по очереди, пока не найдём подходящую пару
    for startId in startPool:

        if budget.exhausted():
            return PairResult.fail(BUDGET_EXCEEDED)

        // 3. Ограниченный BFS от старта, но без конкретной цели — собираем все слои
        layers = boundedBfsLayers(startId, config.maxSteps, budget)

        if layers.reason == BUDGET_EXCEEDED:
            return PairResult.fail(BUDGET_EXCEEDED)

        // 4. Собираем кандидатов-целей из слоёв minSteps..maxSteps
        candidates = []
        for depth in config.minSteps .. config.maxSteps:
            for actorId in layers.byDepth[depth]:
                if actorId == startId: continue
                if config.allowTrivial == false and depth < 2: continue
                candidates.push( (actorId, depth) )

        if candidates is empty:
            continue   // этот старт не дал целей — берём следующий

        // 5. Приоритизируем глубины в "предпочтительном" диапазоне
        candidates = orderByPreference(candidates, config, rng)

        // 6. Пробуем цели с ограничением и проверяем их пригодность
        tried = 0
        for (targetId, depth) in candidates:
            if tried >= config.targetSampleSize: break
            tried += 1

            if targetId in usedActors: continue

            // 6a. Проверяем, что у цели есть хотя бы один фильм (иначе она бесполезна)
            targetMovies = getActorMovieIds(targetId)
            if targetMovies is empty: continue

            // 6b. Опционально: убедимся, что от цели можно вернуться (бидирекциональная проверка)
            if config.requireReverseReachability:
                reverse = bfsShortestPath(targetId, startId, config.maxSteps, budget)
                if not reverse.found: continue

            // 6c. Восстанавливаем путь из BFS-дерева старта
            path = reconstructChain(startId, targetId, layers.parents)

            return PairResult.ok(
                startActorId = startId,
                targetActorId = targetId,
                shortestPath = path,
                distance = depth,
                seed = seed
            )

    return PairResult.fail(NO_VALID_PAIR)
```

### 3.3. BFS со слоями (без цели)

Отличается от `bfsShortestPath` тем, что не останавливается на конкретной цели, а собирает все достигнутые узлы по слоям. Возвращает `parents` для восстановления любого пути.

```text
function boundedBfsLayers(
    startId: PersonId,
    maxSteps: Int,
    budget: Budget
): LayersResult

    visited = { startId }
    frontier = [ startId ]
    parents = {}
    byDepth = { 0: [ startId ] }

    for depth in 1 .. maxSteps:
        if budget.exhausted(): return LayersResult.fail(BUDGET_EXCEEDED)

        nextFrontier = []

        for actorId in frontier:
            budget.nodesVisited += 1

            movies = getActorMovieIds(actorId)
            // ограничение: не раскрываем «хабы» с тысячами фильмов
            movies = limitCandidates(movies, maxFrontierPerActor, rng)

            for movieId in movies:
                castIds = getMovieCastIds(movieId)
                castIds = limitCandidates(castIds, maxFrontierPerActor, rng)

                for nextId in castIds:
                    if nextId == actorId: continue
                    if nextId in visited: continue

                    visited.add(nextId)
                    parents[nextId] = ParentLink(movieId, actorId)
                    nextFrontier.add(nextId)

        if nextFrontier is empty:
            break   // граф исчерпан раньше maxSteps — нормальный выход

        byDepth[depth] = nextFrontier
        frontier = nextFrontier

    return LayersResult.ok(byDepth, parents)
```

### 3.4. Приоритизация целей

```text
function orderByPreference(
    candidates: List<(actorId, depth)>,
    config: PairGenConfig,
    rng: Random
): List<(actorId, depth)>

    preferred = candidates.filter { depth in config.preferredMinSteps..config.preferredMaxSteps }
    others    = candidates.filter { depth not in config.preferredMinSteps..config.preferredMaxSteps }

    shuffle(preferred, rng)
    shuffle(others, rng)

    return preferred + others
```

Порядок приоритетов:

1. Сначала цели из «предпочтительного» диапазона длин (например, 3–5 шагов).
2. Затем всё остальное, что попадает в `minSteps..maxSteps`.
3. Тривиальные пары (`d = 1`) отсекаются, если `allowTrivial = false`.

### 3.5. Почему это гарантирует путь ≤ N

- `boundedBfsLayers` не выходит за `maxSteps`.
- `byDepth[d]` содержит только актёров, реально достигнутых за `d` шагов от старта.
- `parents` позволяет восстановить конкретный путь, что уже подтверждает существование связи.
- `reconstructChain` строит валидную цепочку по этому пути.
- Если такой цели нет ни для одного старта — пара не генерируется, ошибка `NO_VALID_PAIR`.

Отдельной «проверки пути» после генерации не требуется — BFS сам по себе является проверкой.

### 3.6. Ограничения и дефолтные значения

| Параметр | Рекомендуемое значение | Зачем |
|---|---:|---|
| `minSteps` | 2 | Отсекает тривиальные пары |
| `preferredMinSteps` | 3 | «Средняя» сложность |
| `preferredMaxSteps` | 5 | Верхняя граница «интересного» пути |
| `maxSteps` | 6 | Жёсткий предел для BFS генерации |
| `startPoolSize` | 20–30 | Сколько стартовых актёров перебрать |
| `maxFrontierPerActor` | 50–100 | Ограничение fan-out «хабов» |
| `targetSampleSize` | 5–10 | Сколько целей проверить на одном старте |
| `budget.maxRequests` | ~200–400 | Общий лимит на генерацию |
| `budget.maxNodesVisited` | ~1000–2000 | Ограничение размера BFS |
| `budget.maxWallClockMs` | 5000–8000 | Предел времени генерации |

Значения вынесены в `PairGenConfig` и могут зависеть от сложности.

---

## 4. Обработка тупиков

Тупик — состояние, в котором игрок не может построить путь к цели за оставшиеся шаги. Тупики бывают трёх видов.

### 4.1. Классификация

| Вид | Причина | Проверка |
|---|---|---|
| Локальный | У текущего актёра нет фильмов | `getActorMovieIds(current) = ∅` |
| Локальный | Во всех фильмах актёра нет других актёров | `getMovieCastIds(m)` содержит только текущего |
| Глобальный | До цели не добраться за оставшиеся шаги | `bfsShortestPath(current, target, remaining)` → `NOT_FOUND` |
| Бюджетный | Проверка не завершилась | `BUDGET_EXCEEDED` (не считается тупиком) |

### 4.2. Проверка после каждого шага

```text
function checkDeadEnd(session: GameSession, budget: Budget): DeadEndStatus

    // 1. Остались ли шаги?
    remaining = session.maxSteps - session.stepCount
    if remaining <= 0 and session.currentActorId != session.targetActorId:
        return DEAD_END(TOO_MANY_STEPS)

    // 2. Есть ли у актёра фильмы?
    movies = getActorMovieIds(session.currentActorId)
    if movies is empty:
        return DEAD_END(NO_MOVIES)

    // 3. Быстрый локальный тупик: нет фильма с "другим" актёром
    hasOtherActor = false
    for movieId in movies:
        castIds = getMovieCastIds(movieId)
        if castIds.any { it != session.currentActorId }:
            hasOtherActor = true
            break
    if not hasOtherActor:
        return DEAD_END(NO_OTHER_ACTORS)

    // 4. Глобальная проверка: есть ли путь к цели?
    result = bfsShortestPath(
        startId  = session.currentActorId,
        targetId = session.targetActorId,
        maxSteps = remaining,
        budget   = budget
    )

    switch result.reason:
        FOUND:
            return OK(shortestRemaining = result.distance)
        NOT_FOUND:
            return DEAD_END(NO_PATH_WITHIN_REMAINING)
        BUDGET_EXCEEDED:
            return UNKNOWN   // не наказываем игрока за нашу нехватку бюджета
```

### 4.3. Что делает presentation с результатом

- `OK(shortestRemaining)` — партия продолжается. Если `shortestRemaining == remaining`, у игрока только один валидный ход — можно мягко предупредить («Цель где-то рядом»).
- `DEAD_END(...)` — показывается состояние «Нет доступных переходов». Игроку предлагается:
  - `UndoLastTransition`, если последний переход был в текущей сессии и ещё не подтверждён как «финальный»;
  - `FinishGame(surrender)`;
  - новая партия.
- `UNKNOWN` — игра **не** блокирует игрока: интерфейс просто показывает текущее состояние без вердикта. Учитывая, что генератор изначально гарантирует путь, долгая неразрешимость — редкость и связана с бюджетом.

### 4.4. Отмена перехода (Undo)

```text
function undoLastTransition(session: GameSession): GameSession

    if session.steps is empty:
        return session                    // нечего отменять

    last = session.steps.last()
    steps = session.steps.dropLast()

    // текущим снова становится fromActorId последнего шага
    updated = session.copy(
        steps         = steps,
        currentActorId = last.fromActorId,
        stepCount     = steps.size,
        updatedAtMs   = now()
    )

    // сохраняем в Room: активная партия обновляется после каждого подтверждённого перехода
    persist(updated)
    return updated
```

`Undo` **не выполняет сетевых запросов**, если `movie_cast` уже в Room — переходы не удаляются из кэша.

### 4.5. Важная инвариантность

Тупик — это **не ошибка**, а игровое состояние. Presentation должен показывать его спокойно и предлагать действия. Домен **никогда** не должен превращать `NOT_FOUND` в исключение: это валидный результат `bfsShortestPath`.

---

## 5. Оптимизации: как не дёргать API лишний раз

Все оптимизации сводятся к тому, чтобы сетевой вызов был **последним** вариантом, а каждое посещение узла графа — использовалось повторно.

### 5.1. Трёхуровневый кэш чтения

Порядок чтения (из `docs/03_data_layer.md`, повторяем для алгоритма):

```text
getActorMovieIds(personId):
    1. InMemoryCache.personMovies[personId]   → если есть, вернуть
    2. Room: actors + actor_movie_credits     → если свежие по TTL, вернуть
    3. TMDB: GET /person/{id}/movie_credits   → сохранить в Room + InMemoryCache
    4. если сети нет и Room-кэш устарел       → вернуть устаревший Room-кэш
    5. иначе                                  → OfflineError

getMovieCastIds(movieId):
    1. InMemoryCache.movieCast[movieId]
    2. Room: movie_cast + actors
    3. TMDB: GET /movie/{id}/credits
    4. ... (аналогично)
```

Все обращения в BFS идут **через** эти функции, поэтому алгоритм автоматически переиспользует кэш.

### 5.2. Мемоизация на время генерации

Внутри одного вызова `generatePair` держим локальные карты, чтобы не обращаться к репозиторию повторно за тем же ID:

```text
struct GenCache {
    moviesByActor: Map<PersonId, List<MovieId>>
    castByMovie:   Map<MovieId, List<PersonId>>
}

function getActorMovieIds(actorId):
    return genCache.moviesByActor.getOrPut(actorId) { repository.getActorMovieIds(actorId) }

function getMovieCastIds(movieId):
    return genCache.castByMovie.getOrPut(movieId) { repository.getMovieCastIds(movieId) }
```

Локальный кэш живёт **только в пределах одной генерации** и отбрасывается. Глобальный кэш — это Room + in-memory верхнего уровня.

### 5.3. Ограничение fan-out

Часть актёров имеет сотни фильмов, а фильмы — сотни актёров. Раскрывать всё — прямой путь к `429 Too Many Requests`.

```text
function limitCandidates(
    items: List<T>,
    limit: Int,
    rng: Random
): List<T>

    if items.size <= limit: return items
    // детерминированная выборка с seed для воспроизводимости
    return sampleDeterministic(items, limit, rng)
```

- Для `movies` предпочтительнее брать фильмы с наибольшим `popularity` и недавние — они чаще дают «интересные» связи. Это можно предвычислять офлайн (в кэше), не тратя запросы.
- Для `cast` — сохранять порядок `order` из TMDB и брать первые `K`, если нужен «главный состав», или случайную выборку, если нужна вариативность.

### 5.4. Бюджет как единая точка учёта

Все сетевые запросы идут через обёртку:

```text
function fetchWithBudget(request: () -> Response, budget: Budget): Response
    if budget.exhausted():
        throw BudgetExceeded
    budget.requestsUsed += 1
    return request()
```

Алгоритмы никогда не вызывают HTTP напрямую — только `repository.*`, которые либо берут из кэша, либо делают бюджетный сетевой вызов. Это позволяет **одним счётчиком** управлять сложностью генерации и проверки пути.

### 5.5. Ранняя остановка

- BFS останавливается на первом попадании в `targetId`.
- Генерация останавливается на первой валидной паре — не пытается найти «самую лучшую».
- `boundedBfsLayers` не идёт глубже `maxSteps`.
- `checkDeadEnd` сначала делает дешёвые локальные проверки (пустой фильм, пустой cast) и только потом глобальный BFS.

### 5.6. Бидирекциональная проверка (опционально)

Для проверки существования пути между `start` и `target` при генерации можно вести BFS **с двух сторон** и остановиться при пересечении фронтов. Это уменьшает раскрываемую часть графа в разы для пар, которые далеко друг от друга.

```text
function bidirectionalReachable(
    a: PersonId, b: PersonId, maxSteps: Int, budget: Budget
): Bool

    frontA = { a }
    frontB = { b }
    visitedA = { a }
    visitedB = { b }
    stepsA = 0
    stepsB = 0

    while stepsA + stepsB < maxSteps:
        if budget.exhausted(): return UNKNOWN
        if frontA is empty and frontB is empty: return false

        // раскрываем меньший фронт — эвристика балансировки
        if frontA.size <= frontB.size:
            frontA = expandFront(frontA, visitedA, budget)
            stepsA += 1
            if frontA intersects visitedB: return true
        else:
            frontB = expandFront(frontB, visitedB, budget)
            stepsB += 1
            if frontB intersects visitedA: return true

    return false
```

В базовой версии `checkDeadEnd` использует односторонний BFS — его проще кэшировать и валидировать. Бидирекциональность применяется в `requireReverseReachability` при генерации, чтобы отбраковать кандидатов дешевле.

### 5.7. Сохранение решения при генерации

При старте партии сохраняем `shortestPath` (список `ChainStep`) в `GameSessionEntity`:

```text
game_sessions.shortestPathJson   (или отдельная таблица session_solution_steps)
```

Это даёт три преимущества:

1. **Оффлайн-подсказка**: можно показать «цель на расстоянии d», не ходя в TMDB.
2. **Быстрая проверка тупика**: если игрок отклонился от решения, `checkDeadEnd` всё равно может сверить текущее положение с сохранённым путём локально.
3. **Отладка и воспроизводимость**: `generationSeed` + `shortestPath` позволяют восстановить условия партии.

Кэшировать **все** промежуточные варианты не нужно — только минимальный путь. Альтернативные пути остаются в графе и находятся BFS при необходимости.

### 5.8. Ограничение сетевых повторов

- `429 Too Many Requests` — не ошибка данных, а сигнал замедлиться. `Budget` может вести себя как «вежливый клиент»: при `429` ожидание увеличивается экспоненциально, но общий `maxWallClockMs` не даёт генерации тянуться бесконечно.
- Для временных сетевых ошибок (`IOException`, `5xx`) — не более `K` повторов на запрос, с задержкой. По исчерпании — ошибка наверх.
- `4xx` (кроме `429`) — не повторяем, сразу возвращаем доменную ошибку.

### 5.9. Кэш между игроками / сессиями

Данные TMDB (`movie_cast`, `actor_movie_credits`) переиспользуются между сессиями. Поэтому второе открытие того же фильма или актёра почти всегда идёт из кэша. Это снижает средний `requestsUsed` на новую генерацию и позволяет поднимать сложность пар без риска `429`.

Важно: **нельзя** заранее прогревать весь граф по популярным актёрам — это создаёт пиковую нагрузку. Прогрев должен быть опциональным, инкрементальным и ограниченным `Budget`.

---

## 6. Сводная таблица алгоритмов

| Задача | Функция | Предел | Возвращает |
|---|---|---|---|
| Проверка пути игрока | `validateChain` | — | `Valid` / `Invalid` |
| Проверка пути при генерации | `bfsShortestPath` | `maxSteps`, `Budget` | `BfsResult` |
| Генерация пары | `generatePair` | `PairGenConfig`, `Budget` | `PairResult` |
| Раскрытие графа | `boundedBfsLayers` | `maxSteps`, `maxFrontierPerActor`, `Budget` | `LayersResult` |
| Проверка тупика | `checkDeadEnd` | `remaining`, `Budget` | `DeadEndStatus` |
| Отмена перехода | `undoLastTransition` | — | `GameSession` |
| Восстановление пути | `reconstructChain` | — | `List<ChainStep>` |

---

## 7. Границы документа

Этот документ описывает **логику** алгоритмов, а не их реализацию. За его пределами остаются:

- выбор источника для `startPool` (популярные актёры, ручной список, фиксированные seed'ы);
- конкретная стратегия `sampleDeterministic`;
- формулы оценки «интересности» пары и сложности (могут учитывать метрики популярности, число альтернативных путей, разнообразие жанров);
- формат хранения `shortestPath` (JSON, отдельная таблица, бинарный формат);
- кэш изображений и постеров;
- серверная генерация (не входит в MVP).

Эти вопросы решаются на этапах технической реализации и не меняют базовые инварианты: пара генерируется только при существовании пути ≤ `maxSteps`, переход валиден только через `cast`, тупик — валидное игровое состояние, а не ошибка.