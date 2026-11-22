package com.zurus.kinosvyazi.ui.navigation

/**
 * Маршруты всех экранов приложения (docs/01_concept.md, §2).
 *
 * `sessionId` — UUID партии (docs/05_roadmap.md, пункт 4:
 * навигация переживает process death, сессия восстанавливается из Room).
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
    const val MOVIE_PICKER = "movie_picker/{sessionId}"

    /** Экран выбора следующего актёра (§2.6). */
    const val ACTOR_PICKER = "actor_picker/{sessionId}/{movieId}"

    /** Экран подтверждения перехода (§2.7). */
    const val TRANSITION = "transition/{sessionId}/{movieId}/{toActorId}"

    /** Экран победы (§2.8). */
    const val RESULT_WON = "result_won/{sessionId}"

    /** Экран результата без победы (§2.9). */
    const val RESULT_NON_WIN = "result_non_win/{sessionId}"

    /** Экран просмотра цепочки (§2.11). */
    const val CHAIN = "chain/{sessionId}"

    /** Экран ошибки (§2.10). */
    const val ERROR = "error"

    const val SESSION_ID = "sessionId"
    const val MOVIE_ID = "movieId"
    const val TO_ACTOR_ID = "toActorId"

    fun game(sessionId: String): String = GAME.replace("{sessionId}", sessionId)

    fun moviePicker(sessionId: String): String = MOVIE_PICKER.replace("{sessionId}", sessionId)

    fun actorPicker(sessionId: String, movieId: Int): String =
        ACTOR_PICKER.replace("{sessionId}", sessionId).replace("{movieId}", movieId.toString())

    fun transition(sessionId: String, movieId: Int, toActorId: Int): String =
        TRANSITION.replace("{sessionId}", sessionId)
            .replace("{movieId}", movieId.toString())
            .replace("{toActorId}", toActorId.toString())

    fun resultWon(sessionId: String): String = RESULT_WON.replace("{sessionId}", sessionId)

    fun resultNonWin(sessionId: String): String = RESULT_NON_WIN.replace("{sessionId}", sessionId)

    fun chain(sessionId: String): String = CHAIN.replace("{sessionId}", sessionId)
}
