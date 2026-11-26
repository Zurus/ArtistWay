# КиноНить

Мобильная игра «степени кино» для Android: построить цепочку
«актёр → фильм → актёр» от стартового актёра до целевого.
Данные — [TMDB](https://www.themoviedb.org/).

MVP — локальная игра без собственного бэкенда: пара генерируется
и проверяется на устройстве игрока.

**Текущее состояние:** MVP выполнен — все 6 этапов `docs/05_roadmap.md`
(см. статусы в `docs/prompts.md`), приёмка — `docs/QA_CHECKLIST.md`.
Приложение: полная игровая петля (генерация пары → переходы → победа/сдача/тупик),
офлайн-режим, восстановление партии, R8-минифицируемый release-билд.

---

## Возможности (MVP)

- Генерация гарантированно решаемой пары актёров (BFS по графу
  «актёр ↔ фильм», бюджет запросов/узлов/времени).
- Игровая петля: выбор фильма → выбор актёра → подтверждение перехода
  с превью и валидацией цепочки; отмена не фиксирует шаг.
- Проверка тупика после каждого шага (локально + через TMDB), подсказка
  «цель достижима одним переходом», «Отменить шаг» (undo).
- Экраны победы и проигрыша (сдача/тупик/ошибка) с итоговой цепочкой.
- Офлайн: баннер «нет сети», кэш TMDB-данных в Room, сохранённые данные
  доступны без сети; ошибки — понятные тексты и «Повторить».
- Активная партия переживает перезапуск приложения (восстановление из Room).
- Release-сборка с R8 (`isMinifyEnabled` + `isShrinkResources`), правила в
  `app/proguard-rules.pro`.

## Ограничения (осознанные, MVP)

- **Пул актёров зашит** (data-driven): 107 TMDB-актёров, сгенерированные
  `tools/generate_start_pool.py` (97 популярных фильмов, пересечение
  составов ≥ 2), + 10 legacy-звёзд; пары — все N×(N−1) = 13,572
  комбинации. Перегенерация пула — запуск скрипта (нужен TMDB-ключ),
  код не меняется; расширение списка — пост-MVP (P4).
- **Локаль TMDB = `en-US`**: интерфейс на русском, названия фильмов/актёров
  — английские (решение зафиксировано в `docs/01_concept.md`).
- **TMDB заблокирован в РФ**: разработка и игра — через VPN;
  reverse-proxy для обхода — пост-MVP фича.
- **Без лимита шагов у игрока**: `maxSteps` — только глубина BFS при
  генерации; проиграть можно сдачей или тупиком.
- **Тесты на Robolectric** (JVM), не instrumented: в CI нет устройства;
  отклонение документировано (`docs/QA_CHECKLIST.md`, A6/A7).
- **Room-тесты** — через Robolectric, не instrumented (то же отклонение).
- **Release-APK не подписан** (`app-release-unsigned.apk`) — подпись
  ключом дистрибуции не входит в MVP.
- Ключ TMDB лежит в `BuildConfig` (обычный APK — извлекаем); пост-MVP —
  серверный прокси без ключа на клиенте.

---

## Требования

| Компонент | Версия | Где взять |
|---|---|---|
| JDK | **17** (LTS) | [Eclipse Temurin](https://adoptium.net/temurin/releases/) — `JAVA_HOME` должен указывать на него |
| Android Studio | **2024.x (Ladybug) или новее** | [developer.android.com/studio](https://developer.android.com/studio) |
| Android SDK | платформа **35**, build-tools **35.0.1**, platform-tools | ставятся автоматически (SDK Manager) |
| ОС | Windows 10+, macOS, Linux | — |
| ОЗУ | **8 ГБ минимум** | IDE + Gradle |
| Интернет | нужен для первой сборки | Gradle 8.9, зависимости |

Версии стека (каталог `gradle/libs.versions.toml`): AGP 8.7.3, Kotlin 2.1.21
(KSP2), Compose BOM 2024.12.01, Hilt 2.54, Room 2.7.2, Retrofit 2.11.0,
OkHttp 4.12.0, kotlinx-serialization 1.7.3, detekt 1.23.7.

---

## Ключ TMDB

1. Зарегистрируйтесь на [themoviedb.org](https://www.themoviedb.org/)
   (если в РФ — через VPN).
2. **Settings → API** → **Basic (v3)** → скопируйте ключ.
3. Впишите в файл **`local.properties`** в корне проекта:

   ```properties
   TMDB_API_KEY=ваш_ключ
   ```

   Файл создаётся Android Studio автоматически (в нём же путь к SDK).
   Если его нет — создайте вручную, сохранив `sdk.dir=`.
4. Пересоберите проект (Gradle подхватит значение при синхронизации).

Ключ попадает в `BuildConfig.TMDB_API_KEY` только в вашу локальную
сборку: `local.properties` находится в `.gitignore` и в репозиторий
не коммитится.

> Без ключа сборка и unit/UI-тесты проходят (тесты используют in-memory
> двойники репозиториев), но приложение не сможет загрузить реальные
> данные TMDB.

---

## Сборка

```bash
./gradlew assembleDebug    # debug-APK:  app/build/outputs/apk/debug/
./gradlew assembleRelease  # release-APK с R8: app/build/outputs/apk/release/app-release-unsigned.apk
```

Gradle не нужен в системе — используется обёртка `./gradlew`
(при первом запуске скачает Gradle 8.9).

## Тесты и анализ

```bash
./gradlew test        # 171 тест: domain, data, ViewModel, Compose UI (Robolectric)
./gradlew detekt      # статический анализ (конфиг detekt.yml)
./gradlew lint        # Android Lint
```

- **Compose UI-тесты** (`app/src/test/.../ui/MainActivityUiTest.kt`):
  happy path до победы, отмена перехода, тупик, ошибка сети в MoviePicker.
  Hilt-граф в тестах подменяет `RepositoryModule` in-memory двойниками
  (`UiTestRepositoriesModule`, `@TestInstallIn(replaces=…)`), поэтому
  сценарии детерминированы и не зависят от сети.
- **Room-поведение** (восстановление сессии, undo) покрыто Robolectric-
  тестами `GameSessionRepositoryTest`/`GameViewModelTest`.
- Полный чеклист приёмки — `docs/QA_CHECKLIST.md` (включая ручной смоук
  на реальных данных TMDB и замер бюджета на 10 генерациях).

---

## Структура пакетов

Одно модульное приложение `:app`, пакет `com.zurus.cinethread`
(`docs/02_architecture.md`):

| Пакет | Назначение |
|---|---|
| `core/common` | `Result`, `AppError`, `Clock`, диспатчеры |
| `core/ui` | `UiState`, маппер ошибок, `ChainResolver` |
| `data/remote` | TMDB: Retrofit API, DTO, маппинг ошибок, rate-limit, ApiKey-интерцептор |
| `data/local` | Room: сущности, DAO, база, конвертеры |
| `data/repository` | реализации репозиториев домена (TMDB + Room + генератор пар) |
| `data/cache` | in-memory кэш и политика freshness |
| `data/network` | конфигурация сети, наблюдатель офлайна |
| `domain/model` | `Actor`, `Movie`, `GameSession`, `GamePair`, `ChainStep`, `GameResult` |
| `domain/usecase` | игровые UseCase (старт, переход, undo, победа/сдача, тупик) |
| `domain/util` | `Bfs`, `PairGenerator`, `ChainValidator`, `Budget`, `GenCache` |
| `feature/*` | MVVM-фичи: home, rules, game (+setup), moviepicker, actorpicker, transition, chain, result, error |
| `ui/navigation` | маршруты и `NavHost` (аргументы в стеке — переживают process death) |
| `ui/components` | переиспользуемые Compose-компоненты |
| `ui/theme` | Material 3 тема |
| `di` | Hilt-модули (App, Database, Network, Mapper, Repository) |

---

## Документация

| Файл | Назначение |
|---|---|
| `docs/QA_CHECKLIST.md` | чеклист приёмки MVP (автоматика + ручной смоук + бюджет) |
| `docs/01_concept.md` | концепция игры (источник правды по геймплею) |
| `docs/02_architecture.md` | архитектура (Compose, Hilt, Clean Architecture, пакеты) |
| `docs/03_data_layer.md` | data layer (TMDB, Room, офлайн) |
| `docs/04_algorithms.md` | алгоритмы (BFS, генерация пар, тупики) |
| `docs/05_roadmap.md` | план MVP: 6 этапов, файлы, критерии приёмки |
| `docs/prompts.md` | промпты для последовательной работы с ИИ (статусы выполнения) |

> `docs/` — материалы спецификаций (в т.ч. для ИИ); человеческая документация
> — корневые файлы (`README.md`, `docs/QA_CHECKLIST.md`).
