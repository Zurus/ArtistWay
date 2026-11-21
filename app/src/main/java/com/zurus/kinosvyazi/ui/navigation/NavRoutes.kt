package com.zurus.kinosvyazi.ui.navigation

/**
 * Маршруты всех экранов приложения (docs/01_concept.md, §2).
 *
 * На Этапе 1 экраны существуют как composable-заглушки;
 * содержимое реализуется на Этапах 4–5 (docs/05_roadmap.md).
 */
object NavRoutes {

    /** Экран запуска (§2.1). */
    const val HOME = "home"

    /** Экран правил (§2.2). */
    const val RULES = "rules"

    /** Экран генерации партии (§2.3). */
    const val GAME_SETUP = "game_setup"

    /** Экран активной партии (§2.4). */
    const val GAME = "game/{sessionId}"

    /** Экран выбора фильма (§2.5). */
    const val MOVIE_PICKER = "movie_picker/{currentActorId}"

    /** Экран выбора следующего актёра (§2.6). */
    const val ACTOR_PICKER = "actor_picker/{movieId}"

    /** Экран подтверждения перехода (§2.7). */
    const val TRANSITION = "transition/{movieId}/{toActorId}"

    /** Экран победы (§2.8). */
    const val RESULT_WON = "result_won/{sessionId}"

    /** Экран результата без победы (§2.9). */
    const val RESULT_NON_WIN = "result_non_win/{sessionId}"

    /** Экран просмотра цепочки (§2.11). */
    const val CHAIN = "chain/{sessionId}"

    /** Экран ошибки (§2.10). */
    const val ERROR = "error/{errorKey}"

    const val SESSION_ID = "sessionId"
    const val CURRENT_ACTOR_ID = "currentActorId"
    const val MOVIE_ID = "movieId"
    const val TO_ACTOR_ID = "toActorId"
    const val ERROR_KEY = "errorKey"

    fun game(sessionId: String): String = GAME.replace("{sessionId}", sessionId)

    fun moviePicker(currentActorId: Int): String =
        MOVIE_PICKER.replace("{currentActorId}", currentActorId.toString())

    fun actorPicker(movieId: Int): String =
        ACTOR_PICKER.replace("{movieId}", movieId.toString())

    fun transition(movieId: Int, toActorId: Int): String =
        TRANSITION.replace("{movieId}", movieId.toString())
            .replace("{toActorId}", toActorId.toString())

    fun resultWon(sessionId: String): String = RESULT_WON.replace("{sessionId}", sessionId)

    fun resultNonWin(sessionId: String): String = RESULT_NON_WIN.replace("{sessionId}", sessionId)

    fun chain(sessionId: String): String = CHAIN.replace("{sessionId}", sessionId)

    fun error(key: String): String = ERROR.replace("{errorKey}", key)
}
