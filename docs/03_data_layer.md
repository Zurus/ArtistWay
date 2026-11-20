# docs/03_data_layer.md

# Data Layer «КиноСвязи»

Data layer отвечает за TMDB, Room, кэширование и предоставление domain-слою стабильных моделей. TMDB считается внешним, потенциально неполным источником: валидность перехода определяется наличием обоих актёров в `cast` фильма, а не в `crew`. [1]

## 1. TMDB DTO

DTO находятся в `data.remote.dto` и не должны попадать в domain или UI.

### Person DTO

Используется для `/search/person` и `/person/{person_id}`.

```kotlin
data class TmdbPersonDto(
    val id: Int?,
    val name: String?,
    val profilePath: String?,
    val knownForDepartment: String?,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val popularity: Double?
)

data class TmdbPersonSearchResponseDto(
    val page: Int?,
    val results: List<TmdbPersonDto>?,
    val totalPages: Int?,
    val totalResults: Int?
)
```

Обязательные для игры поля:

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Int?` | TMDB `person_id`; без него актёр не может участвовать в переходе |
| `name` | `String?` | Отображаемое имя |
| `profilePath` | `String?` | Относительный путь к фото; URL собирается через TMDB configuration |
| `knownForDepartment` | `String?` | Фильтр по актёрской профессии, если требуется |
| `popularity` | `Double?` | Вспомогательный сигнал для генерации и сортировки |

### Movie DTO

Используется для `/movie/{movie_id}` и данных из `movie_credits`.

```kotlin
data class TmdbMovieDto(
    val id: Int?,
    val title: String?,
    val originalTitle: String?,
    val overview: String?,
    val releaseDate: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val runtime: Int?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val adult: Boolean?
)

data class TmdbMovieSearchResponseDto(
    val page: Int?,
    val results: List<TmdbMovieDto>?,
    val totalPages: Int?,
    val totalResults: Int?
)
```

Обязательные для игры поля:

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Int?` | TMDB `movie_id`; без него фильм не может быть переходом |
| `title` | `String?` | Название для UI |
| `releaseDate` | `String?` | Год выпуска |
| `posterPath` | `String?` | Относительный путь к постеру |
| `adult` | `Boolean?` | Возможный фильтр контента |

### Credits DTO

Используются для `/person/{person_id}/movie_credits` и `/movie/{movie_id}/credits`.

```kotlin
data class TmdbPersonMovieCreditsResponseDto(
    val id: Int?,
    val cast: List<TmdbPersonMovieCreditDto>?,
    val crew: List<TmdbPersonMovieCreditDto>?
)

data class TmdbPersonMovieCreditDto(
    val id: Int?,
    val title: String?,
    val originalTitle: String?,
    val releaseDate: String?,
    val posterPath: String?,
    val character: String?,
    val creditId: String?,
    val adult: Boolean?
)

data class TmdbMovieCreditsResponseDto(
    val id: Int?,
    val cast: List<TmdbMovieCastMemberDto>?,
    val crew: List<TmdbMovieCrewMemberDto>?
)

data class TmdbMovieCastMemberDto(
    val id: Int?,
    val name: String?,
    val originalName: String?,
    val profilePath: String?,
    val character: String?,
    val creditId: String?,
    val order: Int?
)

data class TmdbMovieCrewMemberDto(
    val id: Int?,
    val name: String?,
    val job: String?,
    val department: String?,
    val creditId: String?
)

data class TmdbConfigurationResponseDto(
    val images: TmdbImagesConfigurationDto?
)

data class TmdbImagesConfigurationDto(
    val baseUrl: String?,
    val secureBaseUrl: String?,
    val posterSizes: List<String>?,
    val profileSizes: List<String>?
)
```

Правила обработки:

- Для переходов используются только элементы `cast`; `crew` не создаёт актёрскую связь.
- Поля могут отсутствовать: DTO допускают `null`, а маппер обязан обеспечивать безопасные значения.
- Дубликаты устраняются по `person_id` и `movie_id`.
- `profilePath` и `posterPath` — не полные URL; они дополняются базовым URL из `/configuration`.

## 2. Room Entities

Room хранит только данные, полезные для возобновления партии, повторного использования и офлайн-продолжения.

```kotlin
@Entity(tableName = "actors")
data class ActorEntity(
    @PrimaryKey val personId: Int,
    val name: String,
    val profilePath: String?,
    val knownForDepartment: String?,
    val popularity: Double?,
    val fetchedAtMillis: Long
)

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val movieId: Int,
    val title: String,
    val originalTitle: String?,
    val releaseDate: String?,
    val posterPath: String?,
    val overview: String?,
    val runtime: Int?,
    val voteAverage: Double?,
    val adult: Boolean?,
    val fetchedAtMillis: Long
)

@Entity(
    tableName = "movie_cast",
    primaryKeys = ["movieId", "personId"]
)
data class MovieCastEntity(
    val movieId: Int,
    val personId: Int,
    val character: String?,
    val creditId: String?,
    val order: Int?,
    val fetchedAtMillis: Long
)

@Entity(
    tableName = "actor_movie_credits",
    primaryKeys = ["personId", "movieId"]
)
data class ActorMovieCreditEntity(
    val personId: Int,
    val movieId: Int,
    val character: String?,
    val creditId: String?,
    val fetchedAtMillis: Long
)

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey val sessionId: String,
    val startPersonId: Int,
    val targetPersonId: Int,
    val currentPersonId: Int,
    val status: String,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val finishedAtMillis: Long?,
    val stepCount: Int,
    val generationSeed: Long?
)

@Entity(
    tableName = "chain_steps",
    primaryKeys = ["sessionId", "stepIndex"]
)
data class ChainStepEntity(
    val sessionId: String,
    val stepIndex: Int,
    val fromPersonId: Int,
    val movieId: Int,
    val toPersonId: Int,
    val createdAtMillis: Long
)
```

### Связи

```text
actors 1 ──── N actor_movie_credits N ──── 1 movies
actors 1 ──── N movie_cast N ──── 1 movies

game_sessions 1 ──── N chain_steps
```

| Связь | Смысл |
|---|---|
| `ActorEntity` ↔ `ActorMovieCreditEntity` | Какие фильмы есть в актёрской фильмографии |
| `MovieEntity` ↔ `ActorMovieCreditEntity` | Какие актёры указаны в кредитах фильма |
| `ActorEntity` ↔ `MovieCastEntity` | Кто фактически присутствует в `cast` конкретного фильма |
| `MovieEntity` ↔ `MovieCastEntity` | Актёрский состав фильма |
| `GameSessionEntity` ↔ `ChainStepEntity` | Упорядоченная цепочка активной или завершённой партии |

### DAO

```kotlin
@Dao
interface ActorDao {
    suspend fun upsertActors(actors: List<ActorEntity>)
    suspend fun getActor(personId: Int): ActorEntity?
    suspend fun getActors(personIds: List<Int>): List<ActorEntity>
    suspend fun deleteStaleActors(olderThanMillis: Long)
}

@Dao
interface MovieDao {
    suspend fun upsertMovies(movies: List<MovieEntity>)
    suspend fun getMovie(movieId: Int): MovieEntity?
    suspend fun getMovies(movieIds: List<Int>): List<MovieEntity>
    suspend fun deleteStaleMovies(olderThanMillis: Long)
}

@Dao
interface MovieCastDao {
    suspend fun upsertCast(cast: List<MovieCastEntity>)
    suspend fun getMovieCast(movieId: Int): List<MovieCastEntity>
    suspend fun getMoviesForActor(personId: Int): List<MovieCastEntity>
    suspend fun deleteStaleCast(olderThanMillis: Long)
}

@Dao
interface ActorMovieCreditDao {
    suspend fun upsertCredits(credits: List<ActorMovieCreditEntity>)
    suspend fun getActorMovieCredits(personId: Int): List<ActorMovieCreditEntity>
    suspend fun deleteStaleCredits(olderThanMillis: Long)
}

@Dao
interface GameSessionDao {
    suspend fun upsertSession(session: GameSessionEntity)
    suspend fun getSession(sessionId: String): GameSessionEntity?
    suspend fun getActiveSession(): GameSessionEntity?
    suspend fun updateSession(session: GameSessionEntity)

    suspend fun insertChainSteps(steps: List<ChainStepEntity>)
    suspend fun getChainSteps(sessionId: String): List<ChainStepEntity>
    suspend fun deleteLastChainStep(sessionId: String, stepIndex: Int)
    suspend fun deleteSession(sessionId: String)
}
```

## 3. Retrofit Interfaces

Базовый URL:

```text
[https://api.themoviedb.org/3](https://api.themoviedb.org/3)
```

```kotlin
interface TmdbPersonApi {

    @GET("search/person")
    suspend fun searchPerson(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbPersonSearchResponseDto

    @GET("person/{person_id}")
    suspend fun getPerson(
        @Path("person_id") personId: Int,
        @Query("language") language: String = "en-US"
    ): TmdbPersonDto

    @GET("person/{person_id}/movie_credits")
    suspend fun getPersonMovieCredits(
        @Path("person_id") personId: Int,
        @Query("language") language: String = "en-US"
    ): TmdbPersonMovieCreditsResponseDto
}

interface TmdbMovieApi {

    @GET("movie/{movie_id}")
    suspend fun getMovie(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = "en-US"
    ): TmdbMovieDto

    @GET("movie/{movie_id}/credits")
    suspend fun getMovieCredits(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = "en-US"
    ): TmdbMovieCreditsResponseDto

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbMovieSearchResponseDto
}

interface TmdbConfigurationApi {

    @GET("configuration")
    suspend fun getConfiguration(): TmdbConfigurationResponseDto
}
```

Минимально необходимые эндпоинты для MVP:

```text
GET /search/person
GET /person/{person_id}/movie_credits
GET /movie/{movie_id}/credits
GET /movie/{movie_id}
```

`/configuration` нужен для корректной сборки URL изображений; `/search/movie` не требуется для базового игрового цикла. [1]

## 4. Маппинг DTO ↔ Entity

Мапперы находятся в `data.mapper` и преобразуют данные в обе стороны. DTO остаются приватными для data layer; domain получает только `Actor`, `Movie`, `MovieCredit` и связанные модели.

| Направление | DTO | Entity / Domain | Правила |
|---|---|---|---|
| TMDB → Room | `TmdbPersonDto` | `ActorEntity` | `id` обязателен; отсутствующее имя — безопасная заглушка |
| TMDB → Room | `TmdbMovieDto` | `MovieEntity` | `id` обязателен; `releaseDate` может отсутствовать |
| TMDB → Room | `TmdbMovieCastMemberDto` | `MovieCastEntity` | Сохраняются только валидные `person_id` и `movie_id` |
| TMDB → Room | `TmdbPersonMovieCreditDto` | `MovieEntity` + `ActorMovieCreditEntity` | Фильм сохраняется или обновляется, затем создаётся связь |
| Room → Domain | `ActorEntity` | `Actor` | Фото формируется из `profilePath` и configuration |
| Room → Domain | `MovieEntity` | `Movie` | Год извлекается из `releaseDate`, если он есть |
| Room → Domain | `MovieCastEntity` + `ActorEntity` | `MovieCredit` | Объединяются данные состава и актёра |
| Room → Domain | `GameSessionEntity` + `ChainStepEntity` | `GameSession` | Шаги сортируются по `stepIndex` |

### Ключевые интерфейсы мапперов

```kotlin
interface PersonMapper {
    fun toEntity(dto: TmdbPersonDto, fetchedAtMillis: Long): ActorEntity?
    fun toDomain(entity: ActorEntity): Actor
}

interface MovieMapper {
    fun toEntity(dto: TmdbMovieDto, fetchedAtMillis: Long): MovieEntity?
    fun toDomain(entity: MovieEntity): Movie
}

interface MovieCastMapper {
    fun toEntity(
        dto: TmdbMovieCastMemberDto,
        movieId: Int,
        fetchedAtMillis: Long
    ): MovieCastEntity?

    fun toDomain(
        castEntity: MovieCastEntity,
        actorEntity: ActorEntity
    ): MovieCredit
}

interface PersonMovieCreditMapper {
    fun toMovieEntity(
        dto: TmdbPersonMovieCreditDto,
        fetchedAtMillis: Long
    ): MovieEntity?

    fun toCreditEntity(
        dto: TmdbPersonMovieCreditDto,
        personId: Int,
        fetchedAtMillis: Long
    ): ActorMovieCreditEntity?
}

interface GameSessionMapper {
    fun toEntity(session: GameSession): GameSessionEntity
    fun toDomain(
        sessionEntity: GameSessionEntity,
        steps: List<ChainStepEntity>
    ): GameSession
}
```

## 5. Стратегия кэширования

### Room: долговременный кэш

| Данные | TTL | Инвалидация |
|---|---:|---|
| Актёр: имя, фото-путь, базовые данные | 30 дней | Обновление при следующем успешном запросе |
| Фильм: название, дата, постер, метаданные | 30 дней | Обновление при следующем успешном запросе |
| `movie_cast`: состав конкретного фильма | 7 дней | Обновление при открытии фильма или ошибке валидации |
| `actor_movie_credits`: фильмография актёра | 7 дней | Обновление при открытии фильмографии |
| Активная партия и цепочка | Без TTL | Обновляется после каждого подтверждённого перехода |
| Завершённые партии | Без TTL | Не удаляются автоматически |
| Параметры генерации | Без TTL | Хранятся для воспроизводимости |

### In-memory cache

| Данные | Время жизни | Причина |
|---|---|---|
| Список фильмов текущего экрана | До закрытия экрана / новой загрузки | Быстрый возврат без повторного запроса |
| Список актёров выбранного фильма | До закрытия экрана / новой загрузки | Быстрый возврат без повторного запроса |
| Конфигурация изображений TMDB | 24 часа в памяти | Нужна для сборки URL изображений |
| Предварительный переход | До подтверждения или отмены | Не является зафиксированным шагом |
| Результат последней генерации | До старта новой партии | Временные данные генератора |

### Порядок чтения

```text
Repository
  → In-memory cache
  → Room
  → TMDB
  → Room write-through
  → Domain
```

1. Repository сначала проверяет in-memory cache.
2. Затем проверяет Room и TTL записи.
3. Если запись свежая, TMDB не вызывается.
4. Если записи нет или она устарела, выполняется запрос TMDB.
5. Успешный ответ маппится в Entity и сохраняется в Room.
6. При отсутствии сети используется свежий кэш Room.
7. Если кэша нет, возвращается `Offline` / ошибка загрузки, а не пустой список.

### Инвалидация

- TTL хранится в поле `fetchedAtMillis`.
- Запись считается устаревшей, если `now - fetchedAtMillis > TTL`.
- Устаревшая запись не удаляется сразу: она может использоваться как fallback при отсутствии сети.
- После успешного сетевого запроса выполняется `upsert`: сущность обновляется, а `fetchedAtMillis` перезаписывается.
- При изменении `movie_cast` или `actor_movie_credits` обновляются только затронутые связи, а не весь кэш.
- Периодическая очистка удаляет устаревшие записи актёров, фильмов и кредитов, но не трогает активные и завершённые партии.
- `429 Too Many Requests` не инвалидирует кэш: используется пауза и повтор с задержкой. [1]

## 6. Ошибки и ограничения

| Ситуация | Обработка |
|---|---|
| Нет сети | Возврат Room-кэша, иначе `Offline` |
| TMDB `5xx` | Ошибка сервера, повтор по действию игрока |
| TMDB `429` | Троттлинг и отложенный retry |
| Таймаут | Ошибка сети, повтор по действию игрока |
| `id = null` | Запись не сохраняется и не участвует в переходе |
| Дубликат `person_id` / `movie_id` | Upsert по первичному ключу |
| Пустой `cast` | Возврат пустого списка, но не ошибка сети |
| Неполные поля | Безопасные заглушки в UI |