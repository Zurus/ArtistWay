# docs/05_roadmap.md

# Дорожная карта реализации «КиноСвязи»

Документ разбивает MVP на 6 последовательных этапов. Каждый этап заканчивается работающим инкрементом, который можно запустить, проверить и продемонстрировать. Оценки — в часах для мидл-разработчика, знакомого с Android/Compose/Hilt, но не с этим проектом.

Порядок этапов отражает зависимости: контракты → данные → алгоритмы → UI → edge cases → полировка. Этапы 4 и 5 можно частично вести параллельно после завершения этапа 3.

Суммарная оценка: **~128 часов** (~3–3.5 недели одного разработчика при полной загрузке).

| Этап | Название | Оценка |
|---|---|---:|
| 1 | Фундамент и контракты | ~14 ч |
| 2 | Data layer | ~20 ч |
| 3 | Domain: алгоритмы и UseCase | ~26 ч |
| 4 | MVP игрового цикла (UI) | ~30 ч |
| 5 | Edge cases и экраны результата | ~20 ч |
| 6 | Полировка, тесты, релиз | ~18 ч |

---

## Этап 1. Фундамент и контракты

**Цель:** проект собирается, запускается, Hilt работает, навигация пустая, но корректная, доменные модели и интерфейсы репозиториев определены. Никакой сетевой логики и UI-экранов ещё нет.

**Оценка:** ~14 ч.

### Файлы

Новые:

```text
build.gradle.kts (root)
settings.gradle.kts
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml

app/src/main/java/.../KinoSvyaziApp.kt
app/src/main/java/.../MainActivity.kt
app/src/main/java/.../ui/theme/Theme.kt
app/src/main/java/.../ui/theme/Color.kt
app/src/main/java/.../ui/theme/Type.kt
app/src/main/java/.../ui/navigation/AppNavHost.kt
app/src/main/java/.../ui/navigation/NavRoutes.kt

app/src/main/java/.../di/AppModule.kt
app/src/main/java/.../di/NetworkModule.kt        (заглушка)
app/src/main/java/.../di/DatabaseModule.kt       (заглушка)
app/src/main/java/.../di/RepositoryModule.kt     (заглушка)

core/common/Result.kt
core/common/AppError.kt
core/common/DispatcherQualifiers.kt
core/ui/UiState.kt
core/ui/UiText.kt
core/ui/ErrorMapper.kt

domain/model/Actor.kt
domain/model/Movie.kt
domain/model/MovieCredit.kt
domain/model/GamePair.kt
domain/model/ChainStep.kt
domain/model/GameSession.kt
domain/model/GameResult.kt

domain/repository/ActorRepository.kt
domain/repository/MovieRepository.kt
domain/repository/GamePairRepository.kt
domain/repository/GameSessionRepository.kt
```

### Критерии приёмки

- `./gradlew assembleDebug` проходит без ошибок.
- Приложение запускается, показывает пустой экран с темой, навигация `AppNavHost` содержит маршруты всех экранов (пока пустые composable-заглушки).
- `MainActivity` помечена `@AndroidEntryPoint`; Hilt-граф собирается, тестовый `@HiltAndroidTest` инжектит `Application`.
- Все доменные модели — `data class` с валидными TMDB ID (`Int`), без зависимостей от Android/Room/Retrofit.
- Интерфейсы репозиториев используют только доменные модели и `Result<T, AppError>`.
- `AppError` покрывает: `Network`, `Server`, `RateLimit`, `DataInvalid`, `Offline`, `BudgetExceeded`.
- `./gradlew detekt` (или ktlint) проходит на пустом проекте.
- Unit-тест на `ErrorMapper` для каждого `AppError` → `UiText`.

---

## Этап 2. Data layer

**Цель:** TMDB-данные и Room работают; можно получить фильмографию актёра и cast фильма, закэшировать их и переиспользовать. Сетевые ошибки и `429` корректно классифицируются. Реализации репозиториев соответствуют контрактам этапа 1.

**Оценка:** ~20 ч.

### Файлы

Новые:

```text
data/remote/api/TmdbPersonApi.kt
data/remote/api/TmdbMovieApi.kt
data/remote/api/TmdbConfigurationApi.kt

data/remote/dto/TmdbPersonDto.kt
data/remote/dto/TmdbPersonSearchResponseDto.kt
data/remote/dto/TmdbMovieDto.kt
data/remote/dto/TmdbMovieSearchResponseDto.kt
data/remote/dto/TmdbPersonMovieCreditsResponseDto.kt
data/remote/dto/TmdbPersonMovieCreditDto.kt
data/remote/dto/TmdbMovieCreditsResponseDto.kt
data/remote/dto/TmdbMovieCastMemberDto.kt
data/remote/dto/TmdbMovieCrewMemberDto.kt
data/remote/dto/TmdbConfigurationResponseDto.kt

data/remote/interceptor/ApiKeyInterceptor.kt
data/remote/interceptor/RateLimitInterceptor.kt
data/remote/TmdbErrorMapper.kt

data/network/NetworkConfig.kt
data/network/RetryPolicy.kt

data/local/database/AppDatabase.kt
data/local/database/Converters.kt
data/local/dao/ActorDao.kt
data/local/dao/MovieDao.kt
data/local/dao/MovieCastDao.kt
data/local/dao/ActorMovieCreditDao.kt
data/local/dao/GameSessionDao.kt
data/local/entity/ActorEntity.kt
data/local/entity/MovieEntity.kt
data/local/entity/MovieCastEntity.kt
data/local/entity/ActorMovieCreditEntity.kt
data/local/entity/GameSessionEntity.kt
data/local/entity/ChainStepEntity.kt

data/mapper/PersonMapper.kt
data/mapper/MovieMapper.kt
data/mapper/MovieCastMapper.kt
data/mapper/PersonMovieCreditMapper.kt
data/mapper/GameSessionMapper.kt

data/cache/CachePolicy.kt

data/repository/ActorRepositoryImpl.kt
data/repository/MovieRepositoryImpl.kt
data/repository/GameSessionRepositoryImpl.kt
```

Изменяемые:

```text
app/build.gradle.kts                (+ room, retrofit, okhttp, kotlinx-serialization)
app/src/main/java/.../di/NetworkModule.kt      (реальная конфигурация)
app/src/main/java/.../di/DatabaseModule.kt     (Room)
app/src/main/java/.../di/RepositoryModule.kt   (биндинги реализаций)
```

### Критерии приёмки

- `ActorRepositoryImpl.getActorMovies(personId)` возвращает доменные `Movie`, проходя цепочку in-memory → Room → TMDB. Повторный вызов не делает сетевой запрос (проверяется через MockWebServer).
- `MovieRepositoryImpl.getMovieCast(movieId)` возвращает `List<Actor>` из `cast`, игнорируя `crew`.
- DTO с `id = null` не сохраняются и не возвращаются как валидные данные.
- Дубликаты `person_id` и `movie_id` устраняются upsert-ом; в ответе нет двух одинаковых актёров/фильмов.
- Пустой `cast` возвращает `Success(emptyList())`, а не ошибку сети.
- Ошибки классифицируются: `IOException` → `Offline`/`Network`, `429` → `RateLimit`, `5xx` → `Server`, `4xx` → `DataInvalid`, `null` в критичных полях → `DataInvalid`.
- `RateLimitInterceptor` при `429` повторяет запрос с экспоненциальной задержкой не более `K` раз.
- `GameSessionRepositoryImpl` сохраняет сессию и цепочку в Room, восстанавливает их с сортировкой шагов по `stepIndex`, возвращает `GameSession`.
- Unit-тесты с MockWebServer на: успешный ответ, `429`, `500`, таймаут, пустой `cast`, неполные поля.
- Instrumented-тест Room: upsert актёра/фильма/кредита, чтение, TTL-инвалидация (через подмену `fetchedAtMillis`).

---

## Этап 3. Domain: алгоритмы и UseCase

**Цель:** вся игровая логика из `docs/04_algorithms.md` реализована, покрыта тестами и доступна через UseCase. UI пока не подключён, но домен можно прогонять в unit-тестах.

**Оценка:** ~26 ч.

### Файлы

Новые:

```text
domain/util/Budget.kt
domain/util/GenCache.kt
domain/util/ChainValidator.kt
domain/util/PairGenerator.kt
domain/util/Bfs.kt

domain/usecase/StartGameUseCase.kt
domain/usecase/GetActorMoviesUseCase.kt
domain/usecase/GetMovieCastUseCase.kt
domain/usecase/ValidateTransitionUseCase.kt
domain/usecase/CommitTransitionUseCase.kt
domain/usecase/UndoLastTransitionUseCase.kt
domain/usecase/FinishGameUseCase.kt
domain/usecase/GetGameSessionUseCase.kt
domain/usecase/CheckDeadEndUseCase.kt

data/repository/GamePairRepositoryImpl.kt
```

Изменяемые:

```text
app/src/main/java/.../di/AppModule.kt          (биндинги UseCase, Budget factory)
app/src/main/java/.../di/RepositoryModule.kt   (+ GamePairRepository)
```

### Критерии приёмки

- `StartGameUseCase` возвращает `GamePair` с гарантированным `shortestPath` длиной ≤ `config.maxSteps`; при исчерпании `Budget` возвращает `BudgetExceeded`; при отсутствии целей — `NoValidPair`.
- `PairGenerator` воспроизводим: одинаковый `seed` даёт одинаковую пару (при одинаковом кэше).
- `bfsShortestPath` возвращает кратчайшее расстояние и восстанавливаемый путь; на графе с циклом A→B→A не зацикливается.
- `validateChain` отклоняет: разрыв цепочки, повтор актёра, повтор фильма, отсутствие актёра в `cast`, `null` ID.
- `checkDeadEnd` различает `OK(shortestRemaining)`, `DEAD_END(...)`, `UNKNOWN`; локальные проверки выполняются до глобального BFS.
- `undoLastTransition` удаляет последний шаг и корректно восстанавливает `currentActorId`; сессия сохраняется в Room.
- `CommitTransitionUseCase` добавляет шаг, обновляет `currentActorId`, проверяет победу (`current == target`), фиксирует сессию в Room.
- `FinishGameUseCase` формирует `GameResult` с причиной (`WIN`, `SURRENDER`, `DEAD_END`, `ERROR`) и полной цепочкой.
- Unit-тесты с фейковыми репозиториями (in-memory граф) на:
  - генерацию пары на маленьком графе (≤ 20 актёров);
  - отсечение тривиальных пар (`d = 1` при `allowTrivial = false`);
  - восстановление пути и его валидность;
  - все ветки `checkDeadEnd`;
  - `BudgetExceeded` во всех алгоритмах;
  - `undo` на пустой и непустой цепочке.
- Покрытие `domain/util/*` unit-тестами ≥ 80% по строкам.

---

## Этап 4. MVP игрового цикла (UI)

**Цель:** игрок может пройти партию от запуска до победы на реальных данных TMDB. Пять экранов основного пути работают end-to-end.

**Оценка:** ~30 ч.

### Файлы

Новые:

```text
feature/home/HomeScreen.kt
feature/home/HomeViewModel.kt
feature/home/HomeUiState.kt

feature/rules/RulesScreen.kt

feature/game/GameSetupScreen.kt
feature/game/GameSetupViewModel.kt
feature/game/GameSetupUiState.kt

feature/game/GameScreen.kt
feature/game/GameViewModel.kt
feature/game/GameUiState.kt

feature/moviepicker/MoviePickerScreen.kt
feature/moviepicker/MoviePickerViewModel.kt
feature/moviepicker/MoviePickerUiState.kt

feature/actorpicker/ActorPickerScreen.kt
feature/actorpicker/ActorPickerViewModel.kt
feature/actorpicker/ActorPickerUiState.kt

feature/transition/TransitionScreen.kt
feature/transition/TransitionViewModel.kt
feature/transition/TransitionUiState.kt

feature/result/GameResultScreen.kt
feature/result/GameResultViewModel.kt
feature/result/GameResultUiState.kt

ui/components/ActorCard.kt
ui/components/MovieCard.kt
ui/components/ChainView.kt
ui/components/LoadingState.kt
```

Изменяемые:

```text
app/src/main/java/.../ui/navigation/AppNavHost.kt   (реальные маршруты и аргументы)
app/src/main/java/.../ui/navigation/NavRoutes.kt
```

### Критерии приёмки

- Полный путь: Home → Rules (опционально) → GameSetup → Game → MoviePicker → ActorPicker → Transition → Game → … → Victory проходит с реальным TMDB-ключом.
- На экране `Game` отображаются: стартовый актёр, цель, текущий актёр, цепочка, число шагов, таймер; кнопки «Выбрать фильм», «Сдаться».
- `MoviePicker` показывает фильмы с постером (если есть), названием, годом; пустой список → «Фильмы не найдены»; ошибка загрузки → сообщение и кнопка «Повторить».
- `ActorPicker` показывает актёров с фото, именем, ролью; целевой актёр помечен «Цель»; текущий актёр исключён из списка.
- `Transition` показывает предпросмотр шага (`A → M → B`) и кнопки «Подтвердить» / «Отменить»; отмена возвращает на `ActorPicker` без фиксации шага.
- Победа определяется, когда `currentActorId == targetActorId` после `CommitTransition`; открывается `GameResult` с цепочкой и метриками.
- `HomeScreen` показывает название игры, кнопки «Начать игру», «Как играть», индикатор доступности TMDB (при ошибке — «Повторить подключение»).
- Все ViewModel получают зависимости через `@HiltViewModel`; UI-состояния — sealed `UiState` с `Loading/Content/Empty/Error`.
- Навигация переживает process death (использовать `rememberSaveable` для `sessionId`; сессия восстанавливается из Room).
- Smoke-тест вручную: 3 партии подряд без краша; в одной из них — неверный выбор фильма (список пустой), корректно обработан.

---

## Этап 5. Edge cases и экраны результата

**Цель:** все состояния из `docs/01_concept.md` §6 и `docs/04_algorithms.md` §4 имеют UI и обработку. Игрок не может попасть в «зависшее» или неинформативное состояние.

**Оценка:** ~20 ч.

### Файлы

Новые:

```text
feature/result/GameResultNonWinScreen.kt        (или расширение GameResultScreen)
feature/result/GameResultNonWinViewModel.kt

feature/error/ErrorScreen.kt
feature/error/ErrorViewModel.kt

feature/chain/ChainScreen.kt
feature/chain/ChainViewModel.kt
feature/chain/ChainUiState.kt

ui/components/OfflineBanner.kt
ui/components/DeadEndDialog.kt
ui/components/EmptyState.kt
```

Изменяемые:

```text
feature/game/GameViewModel.kt        (+ CheckDeadEndUseCase, undo)
feature/game/GameScreen.kt           (+ кнопка «Отменить шаг», диалог тупика)
feature/result/GameResultViewModel.kt (+ причина завершения)
feature/moviepicker/MoviePickerViewModel.kt (+ offline/empty)
feature/actorpicker/ActorPickerViewModel.kt (+ offline/empty)
ui/navigation/AppNavHost.kt          (+ error, chain, result_non_win)
```

### Критерии приёмки

- `ErrorScreen` различает: нет сети, TMDB недоступен, `429`, пустой/некорректный ответ, ошибка генерации; для каждой — понятный текст и кнопка «Повторить» / «В меню».
- `GameResultNonWin` показывает причину (`SURRENDER`, `DEAD_END`, `NEW_GAME`, `ERROR`) и частичную цепочку, если она есть.
- `ChainScreen` открывается из результата и из `Game`; отображает всю цепочку в формате `Актёр → Фильм → Актёр`, метрики, дату; поддерживает свёрнутый вид для длинных цепочек (последние 3 шага + «Показать всё»).
- `CheckDeadEndUseCase` вызывается после каждого `CommitTransition`; при `DEAD_END` показывается диалог с кнопками «Отменить шаг», «Сдаться», «Новая партия».
- `Undo` доступен на `GameScreen` (кнопка «Отменить шаг»), работает без сетевых запросов, если `movie_cast` в Room; после undo `currentActorId` корректно восстанавливается.
- При закрытии приложения во время партии и повторном открытии — активная сессия восстанавливается из Room (`GetGameSessionUseCase`), UI показывает актуальное состояние.
- `OfflineBanner` показывается, когда репозиторий вернул кэш из-за отсутствия сети.
- Пустые состояния: «У этого актёра нет доступных фильмов», «В этом фильме не найдено других актёров», «Целевой актёр не найден в cast» — каждое с корректным действием (Назад / Сдаться / Повторить).
- Ручной чек-лист edge cases из `docs/01_concept.md` §6 пройден: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.11, 6.12, 6.16, 6.17 — каждое воспроизведено и обработано.

---

## Этап 6. Полировка, тесты, релиз

**Цель:** приложение готово к внутреннему релизу: стабильно, покрыто тестами, работает в release-сборке, не падает на реальных данных.

**Оценка:** ~18 ч.

### Файлы

Новые:

```text
app/src/test/java/.../domain/*            (дополнение unit-тестов)
app/src/androidTest/java/.../ui/*         (UI-тесты ключевых флоу)
app/src/androidTest/java/.../data/*       (instrumented Room + MockWebServer)

app/proguard-rules.pro                    (правила для Retrofit/Room/serialization)
app/build.gradle.kts                      (release buildType, signing config)
README.md
docs/QA_CHECKLIST.md
```

Изменяемые:

```text
app/build.gradle.kts                      (baseline profile, shrinker)
app/src/main/.../di/AppModule.kt          (финальные параметры Budget, PairGenConfig)
data/cache/CachePolicy.kt                 (выверенные TTL)
domain/util/PairGenerator.kt              (тюнинг maxFrontierPerActor, targetSampleSize)
```

### Критерии приёмки

- Release-сборка (`assembleRelease`) собирается с minify, приложение не падает на реальном TMDB (smoke-тест).
- `Budget` и `PairGenConfig` подобраны так, что 10 подряд генераций на реальных данных укладываются в `maxRequests ≤ 400` и `maxWallClockMs ≤ 8с` каждая; замер зафиксирован в `docs/QA_CHECKLIST.md`.
- UI-тесты (Compose) покрывают: happy path до победы, отмену перехода, тупик, ошибку сети на `MoviePicker`.
- Instrumented-тесты Room: восстановление активной сессии, undo, миграция (если есть).
- Unit-тесты домена: покрытие `domain/util/*` ≥ 80%, все ветки `checkDeadEnd` и `BudgetExceeded`.
- `./gradlew lint detekt test` проходит без ошибок; предупреждения — не более N (фиксируется в README).
- Нет `TODO`/`FIXME` в коде, кроме явно задокументированных в README «Известные ограничения».
- README описывает: как получить TMDB-ключ, как собрать, как запустить тесты, известные ограничения, структуру пакетов.
- Прогон QA-чеклиста: 10 партий подряд, включая 3 с намеренными ошибками/тупиками, без крашей; время восстановления сессии после process death — < 1с.

---

## Зависимости между этапами

```text
Этап 1 ──► Этап 2 ──► Этап 3 ──► Этап 4 ──► Этап 5 ──► Этап 6
              │           │           │
              └───────────┴───────────┘
             параллельные тесты возможны
```

- Этап 2 не начинается без контрактов этапа 1.
- Этап 3 не начинается без работающих репозиториев этапа 2 (иначе алгоритмы не на чем прогонять).
- Этап 4 можно начинать параллельно с этапом 3 на моках домена (ViewModel + фейковые UseCase), но финальная интеграция — после завершения этапа 3.
- Этап 5 требует работающего MVP из этапа 4.
- Этап 6 — финальный, но unit-тесты домена стоит писать уже на этапе 3.

---

## Что не входит в MVP

Следующие пункты сознательно отложены и не должны блокировать этапы 1–6 (см. `docs/01_concept.md` §9):

- Мультиплеер, рейтинги, достижения.
- Ручной ввод имён актёров/фильмов (поиск по названию).
- Серверная генерация пар и общий кэш между устройствами.
- Прогрев графа популярных актёров.
- Локализация (MVP — русский UI + английские названия из TMDB).
- Монетизация и внутренняя валюта.

Эти механики могут быть добавлены без изменения базовых инвариантов: переход только через `cast`, пара генерируется только при существовании пути ≤ `maxSteps`, тупик — валидное игровое состояние.