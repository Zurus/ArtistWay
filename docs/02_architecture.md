# docs/02_architecture.md

# Архитектура «КиноНить»

Приложение строится по Clean Architecture с разделением на `domain`, `data` и `presentation`. Игровая логика не зависит от TMDB, Room, UI или DI-фреймворка; внешние источники данных и интерфейс подключаются через интерфейсы доменного слоя.

## 1. Слои Clean Architecture

```text
presentation
    ↓ вызывает
domain
    ↑ реализуется
data
```

| Слой | Ответственность | Что содержит | Чего не содержит |
|---|---|---|---|
| `domain` | Бизнес-правила игры, модели, сценарии использования | Сущности, интерфейсы репозиториев, UseCase, правила валидации переходов, генератор пары, модель состояния партии | Android, Compose, Retrofit, Room, TMDB DTO, UI-состояния |
| `data` | Получение, кэширование и предоставление данных | Реализации Repository, TMDB API, Room, DTO, мапперы, сетевые политики, офлайн-кэш | Правил построения цепочки, UI, ViewModel |
| `presentation` | Отображение состояния и пользовательские действия | Экраны, ViewModel, UI-состояния, навигация, обработка ошибок для игрока | Прямых обращений к TMDB, Room, бизнес-алгоритмов |

### Domain

Содержит:

- Модели: `Actor`, `Movie`, `MovieCredit`, `GamePair`, `ChainStep`, `GameSession`, `GameResult`.
- Интерфейсы: `ActorRepository`, `MovieRepository`, `GamePairRepository`, `GameSessionRepository`.
- UseCase:
  - `StartGameUseCase`
  - `GetActorMoviesUseCase`
  - `GetMovieCastUseCase`
  - `ValidateTransitionUseCase`
  - `CommitTransitionUseCase`
  - `UndoLastTransitionUseCase`
  - `FinishGameUseCase`
  - `GetGameSessionUseCase`
- Правила:
  - Переход возможен только через фильм, в котором есть и текущий, и следующий актёр.
  - Актёры и фильмы идентифицируются по TMDB ID.
  - Дубликаты актёров и фильмов устраняются по ID.
  - Повторное использование актёра и фильма в одной цепочке запрещено в базовой версии.
  - Партия начинается только после подтверждения существования пути.
  - Цель достигнута, когда текущий актёр совпадает с целевым.

### Data

Содержит:

- `remote`: TMDB API, Retrofit-сервисы, DTO, обработка ошибок и `429 Too Many Requests` (включая `TmdbRequestLogInterceptor` — файловый лог запросов, D6).
- `local`: Room database, DAO, entity, миграции.
- `repository`: реализации domain-интерфейсов.
- `pairsource`: реализации стратегии [PairSource] — источники пары для старта (D8: онлайн, банк, remote-stub, auto).
- `bank`: загрузчик и модель оффлайн-банка пар `pairs_bank_v1.json` (D4/D7).
- `mapper`: преобразование DTO ↔ domain-модели и entity ↔ domain-модели.
- `network`: таймауты, retry, rate limiting, классификация сетевых ошибок.
- `cache`: политики TTL, инвалидации и очистки кэша.

### Presentation

Содержит:

- Экраны: запуск, правила, генерация партии, активная партия, выбор фильма, выбор актёра, подтверждение перехода, победа, результат без победы, ошибка, просмотр цепочки.
- ViewModel для каждого экрана.
- UI-состояния: `Loading`, `Content`, `Empty`, `Error`, `Offline`.
- Навигацию между экранами.
- Отображение понятных пользователю ошибок вместо технических деталей.

## 2. UI-фреймворк: Jetpack Compose

**Выбор: Jetpack Compose.**

| Критерий | Compose | XML + View |
|---|---|---|
| Модель UI | Декларативная: экран — функция от `UiState` | Имплицитная, обновление вручную |
| Объём кода | Меньше: нет findViewById и glue-кода | Больше |
| Анимации цепочки | Анимации и переходы из коробки | Реализация сложнее |
| Экосистема | Официальный UI-набор Android (Navigation, Material3) | Legacy |
| Подход MVVM | `ViewModel + StateFlow + collectAsStateWithLifecycle` | То же, но с ручным биндингом |

Решение влияет на `presentation`-слой: экраны — composable-функции, состояние — sealed-классы, навигация — `Navigation Compose`.

## 3. Структура пакетов

```text
app/
├── di/
│   ├── AppModule
│   ├── NetworkModule
│   ├── DatabaseModule
│   ├── RepositoryModule
│   └── PairSourceModule
├── ui/
│   ├── navigation/
│   ├── theme/
│   └── components/
├── feature/
│   ├── home/
│   ├── rules/
│   ├── game/
│   ├── moviepicker/
│   ├── actorpicker/
│   ├── transition/
│   ├── result/
│   ├── error/
│   └── chain/
└── MainActivity

core/
├── common/
│   ├── Result
│   ├── AppError
│   └── DispatcherQualifiers
├── ui/
│   ├── UiState
│   ├── UiText
│   └── ErrorMapper

domain/
├── model/
│   ├── Actor
│   ├── Movie
│   ├── MovieCredit
│   ├── GamePair
│   ├── ChainStep
│   ├── GameSession
│   └── GameResult
├── repository/
│   ├── ActorRepository
│   ├── MovieRepository
│   ├── GamePairRepository
│   ├── GameSessionRepository
│   └── PairSource
├── usecase/
│   ├── StartGameUseCase
│   ├── GetActorMoviesUseCase
│   ├── GetMovieCastUseCase
│   ├── ValidateTransitionUseCase
│   ├── CommitTransitionUseCase
│   ├── UndoLastTransitionUseCase
│   ├── FinishGameUseCase
│   └── GetGameSessionUseCase
└── util/
    ├── ChainValidator
    └── PairGenerator

data/
├── remote/
│   ├── api/
│   │   ├── TmdbPersonApi
│   │   ├── TmdbMovieApi
│   │   └── TmdbConfigurationApi
│   ├── dto/
│   ├── interceptor/
│   │   ├── ApiKeyInterceptor
│   │   └── TmdbRequestLogInterceptor
│   └── TmdbErrorMapper
├── network/
│   ├── NetworkConfig
│   └── RetryPolicy
├── local/
│   ├── database/
│   │   ├── AppDatabase
│   │   └── Converters
│   ├── dao/
│   │   ├── ActorDao
│   │   ├── MovieDao
│   │   ├── MovieCastDao
│   │   ├── ActorMovieCreditDao
│   │   └── GameSessionDao
│   └── entity/
├── repository/
│   ├── ActorRepositoryImpl
│   ├── MovieRepositoryImpl
│   ├── GamePairRepositoryImpl
│   └── GameSessionRepositoryImpl
├── pairsource/
│   ├── OnlinePairSource
│   ├── BankPairSource
│   ├── RemotePairSource
│   └── AutoPairSource
├── bank/
│   ├── PairBank
│   └── PairBankLoader
├── mapper/
└── cache/
    └── CachePolicy
```

## 4. MVVM

### ViewModel

| ViewModel | Экран | Ответственность |
|---|---|---|
| `HomeViewModel` | Запуск | Старт новой партии, доступность правил, состояние подключения |
| `RulesViewModel` | Правила | Отображение правил, переход к игре |
| `GameSetupViewModel` | Генерация партии | Запуск генерации, прогресс, ошибки, повтор |
| `GameViewModel` | Активная партия | Текущий актёр, цель, цепочка, шаги, таймер, действия игрока |
| `MoviePickerViewModel` | Выбор фильма | Загрузка и отображение фильмов текущего актёра |
| `ActorPickerViewModel` | Выбор актёра | Загрузка и отображение актёров выбранного фильма |
| `TransitionViewModel` | Подтверждение | Предварительный переход, подтверждение или отмена |
| `GameResultViewModel` | Победа / результат | Метрики, причина завершения, действия после партии |
| `ChainViewModel` | Просмотр цепочки | Полная цепочка, метрики, возврат |
| `ErrorViewModel` | Ошибка | Человекочитаемый текст ошибки, повтор действия, возврат в меню |

### UseCase

| UseCase | Вход | Выход | Основная роль |
|---|---|---|---|
| `StartGameUseCase` | Параметры сложности | `GameSession` | Генерирует решаемую пару и создаёт партию |
| `GetActorMoviesUseCase` | `personId` | Список `Movie` | Получает фильмы с актёрскими ролями |
| `GetMovieCastUseCase` | `movieId` | Список `Actor` | Получает актёрский состав фильма |
| `ValidateTransitionUseCase` | Текущий актёр, фильм, следующий актёр | Результат валидации | Проверяет допустимость шага |
| `CommitTransitionUseCase` | Подтверждённый переход | Обновлённая `GameSession` | Добавляет шаг и определяет победу |
| `UndoLastTransitionUseCase` | Текущая сессия | Предыдущая `GameSession` | Отменяет последний переход |
| `FinishGameUseCase` | Сессия, причина | `GameResult` | Фиксирует победу, сдачу, ошибку или тупик |
| `GetGameSessionUseCase` | `sessionId` | `GameSession` | Восстанавливает активную партию |

### Repository

| Repository | Источники | Назначение |
|---|---|---|
| `ActorRepositoryImpl` | TMDB + Room + память | Поиск актёра, данные актёра, фильмография |
| `MovieRepositoryImpl` | TMDB + Room + память | Детали фильма и актёрский состав |
| `GamePairRepositoryImpl` | Domain-генератор + Actor/Movie Repository | Поиск и валидация пары |
| `GameSessionRepositoryImpl` | Room + память | Сохранение и восстановление активной партии |

## 5. Dependency Injection: Hilt

**Выбор: Hilt.**

Hilt выбран, потому что проект предполагает Android-приложение с ViewModel, Room, Retrofit и несколькими экранами. Hilt предоставляет стандартную интеграцию с Android-компонентами, уменьшает ручную настройку и хорошо подходит для командной разработки.

| Критерий | Hilt | Koin |
|---|---|---|
| Интеграция с Android | Встроенная: `@HiltViewModel`, `@AndroidEntryPoint` | Требует дополнительной ручной настройки |
| Компиляция | Проверки на этапе компиляции | Большинство ошибок проявляются в runtime |
| Room и Retrofit | Стандартные модули и квалификаторы | Возможна, но больше ручной конфигурации |
| Кривая входа | Выше из-за аннотаций и кодогенерации | Ниже, DSL проще |
| Соответствие задаче | Предпочтителен для масштабируемого Android-приложения | Подходит для небольшого прототипа |

### Модули DI

| Модуль | Предоставляет |
|---|---|
| `NetworkModule` | Retrofit, OkHttp, TMDB API, интерцепторы (лог запросов — только при `tmdb.logging`) |
| `DatabaseModule` | Room database, DAO |
| `RepositoryModule` | Реализации domain-репозиториев |
| `AppModule` | Диспетчеры, часы, `PairGenConfig`, фабрики `Budget`, `PairBankLoader`, UseCase |
| `PairSourceModule` | Стратегия `PairSource` по `BuildConfig.PAIR_SOURCE` (D8) |

## 6. Офлайн-стратегия

Приложение не обещает полноценную игру без сети: генерация новой партии и первичная загрузка данных требуют TMDB. Однако уже начатую партию и ранее загруженные данные можно продолжать из локального кэша.

```text
UI-действие
  → ViewModel
  → UseCase
  → Repository
  → Room / память / TMDB
```

### Room: долговременный кэш

| Данные | Хранение | Зачем |
|---|---|---|
| Актёры: `person_id`, имя, фото-путь | Room | Быстрое отображение и повторное использование |
| Фильмы: `movie_id`, название, дата, постер-путь | Room | Быстрое отображение и повторное использование |
| Кредиты: связь актёра и фильма, роль | Room | Восстановление доступных переходов |
| Активная партия: старт, цель, текущий актёр, цепочка, шаги, время, найденный путь | Room | Восстановление после закрытия приложения |
| Завершённые партии (результат выводится из `game_sessions` + `chain_steps`) | Room | История и метрики |
| Параметры последней генерации | Room | Воспроизводимость и отладка |

### Только в памяти

| Данные | Хранение | Почему не в Room |
|---|---|---|
| Текущий список фильмов на экране выбора | In-memory cache | Нужен только для текущего экрана |
| Текущий список актёров выбранного фильма | In-memory cache | Нужен только для текущего экрана |
| Состояние загрузки, ошибки, прогресс | ViewModel / UI state | Не является бизнес-данными |
| Предварительный неподтверждённый переход | ViewModel | Не должен сохраняться как часть партии |
| Кэш изображений постеров и фото | Coil / image loader | Отдельный кэш изображений |
| Временные результаты генерации пары | In-memory cache | Отбрасываются после старта партии |

### Политика запросов

- Сначала Repository проверяет Room.
- При отсутствии или устаревании данных выполняется TMDB-запрос.
- Успешный ответ сохраняется в Room и может быть помещён в краткосрочный in-memory cache.
- При отсутствии сети Repository возвращает кэшированные данные, если они достаточны для текущего действия.
- Если кэша нет, UI получает состояние `Offline` с возможностью повторить запрос.
- `429` обрабатывается отдельно: пауза, повтор с задержкой, понятное сообщение игроку.
- Незавершённая партия сохраняется в Room после каждого подтверждённого перехода.
- Неподтверждённый выбор фильма или актёра не сохраняется в Room.

## 7. Поток данных

```text
Игрок выбирает фильм
  → MoviePickerViewModel
  → GetActorMoviesUseCase
  → ActorRepository
  → Room cache → TMDB при необходимости
  → Domain-модель
  → UI state

Игрок выбирает актёра
  → ActorPickerViewModel
  → GetMovieCastUseCase
  → MovieRepository
  → Room cache → TMDB при необходимости
  → Domain-модель
  → UI state

Игрок подтверждает шаг
  → TransitionViewModel
  → ValidateTransitionUseCase
  → CommitTransitionUseCase
  → GameSessionRepository
  → Room
  → Обновлённая GameSession
  → GameViewModel
```

## 8. Обработка ошибок

| Ситуация | Слой | Поведение |
|---|---|---|
| Нет интернета | Data / Presentation | Repository возвращает кэш либо `Offline`; UI показывает понятное сообщение |
| TMDB недоступен | Data | Ошибка сети, повтор по действию пользователя |
| `429 Too Many Requests` | Data | Троттлинг, отложенный retry, сообщение о лимите |
| Пустой или некорректный ответ | Data | Ответ не считается валидным, показывается ошибка загрузки |
| Нет фильмов у актёра | Domain | Состояние «нет доступных переходов» |
| Нет других актёров в фильме | Domain | Состояние «нет других актёров», возврат к выбору фильма |
| Повтор актёра или фильма | Domain | Запрет выбора в базовой версии |
| Закрытие приложения | Data | Активная партия восстанавливается из Room |

## 9. Выбор пары: стратегии (PairSource)

«Откуда взять пару» для новой партии вынесено в стратегию (D8,
`docs/debugging.md`): код выше data-слоя знает только доменное
интерфейс [PairSource], конкретный источник выбирается на время сборки.

```kotlin
interface PairSource {
    suspend fun pick(config: PairGenConfig, seed: Long?): Result<GamePair, PairGenerationError>
}
```

| `pairSource` (local.properties) | Стратегия | Поведение |
|---|---|---|
| `auto` (по умолчанию) | `AutoPairSource` | глубина покрыта банком → банк (0 запросов), иначе — онлайн |
| `online` | `OnlinePairSource` | адаптер над `GamePairRepositoryImpl` (бюджеты D1, d=2-поиск D5) — текущее поведение |
| `bank` | `BankPairSource` | только банк; глубина не покрыта → `NoValidPair` (без fallback) |
| `remote` | `RemotePairSource` | заглушка P3: понятный `DataError`, без краша |

- Выбор — в `di/PairSourceModule` (`when` по `BuildConfig.PAIR_SOURCE`;
  неизвестное значение → `auto` + `Log.w`).
- `StartGameUseCase` принимает `PairGenConfig.forDepth(depth)` + seed и
  не знает источник; глубина 2..4 валидируется в use case.
- Без дублирования: BFS, бюджеты, загрузчик банка — только оборачиваются.
- `PairGenerator` (`domain/util`, пуловой BFS) — не часть старта,
  основа P4 (docs/04_algorithms.md §5.4).