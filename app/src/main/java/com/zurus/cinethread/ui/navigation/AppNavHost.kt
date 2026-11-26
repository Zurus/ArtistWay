package com.zurus.cinethread.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zurus.cinethread.feature.actorpicker.ActorPickerScreen
import com.zurus.cinethread.feature.chain.ChainScreen
import com.zurus.cinethread.feature.error.ErrorScreen
import com.zurus.cinethread.feature.error.ErrorType
import com.zurus.cinethread.feature.game.GameScreen
import com.zurus.cinethread.feature.game.GameSetupScreen
import com.zurus.cinethread.feature.home.HomeScreen
import com.zurus.cinethread.feature.moviepicker.MoviePickerScreen
import com.zurus.cinethread.feature.result.GameResultNonWinScreen
import com.zurus.cinethread.feature.result.GameResultScreen
import com.zurus.cinethread.feature.rules.RulesScreen
import com.zurus.cinethread.feature.transition.TransitionScreen

/**
 * Граф навигации приложения (docs/01_concept.md, §2, docs/05_roadmap.md, пункт 4).
 *
 * Основной путь: Home → Rules (опционально) → GameSetup → Game →
 * MoviePicker → ActorPicker → Transition → Game → … → Result.
 *
 * Аргументы (sessionId, movieId, toActorId) сохраняются в стеке навигации
 * и переживают process death; данные экраны восстанавливают состояние
 * из Room по `sessionId`.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
    ) {
        composable(NavRoutes.HOME) { HomeScreen(navController) }
        composable(NavRoutes.RULES) { RulesScreen(navController) }
        composable(NavRoutes.GAME_SETUP) { GameSetupScreen(navController) }
        composable(
            route = NavRoutes.GAME,
            arguments = listOf(navArgument(NavRoutes.SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            GameScreen(navController, entry.requireSessionId())
        }
        composable(
            route = NavRoutes.MOVIE_PICKER,
            arguments = listOf(navArgument(NavRoutes.SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            MoviePickerScreen(navController, entry.requireSessionId())
        }
        composable(
            route = NavRoutes.ACTOR_PICKER,
            arguments = listOf(
                navArgument(NavRoutes.SESSION_ID) { type = NavType.StringType },
                navArgument(NavRoutes.MOVIE_ID) { type = NavType.IntType },
            ),
        ) { entry ->
            ActorPickerScreen(
                navController = navController,
                sessionId = entry.requireSessionId(),
                movieId = entry.requireMovieId(),
            )
        }
        composable(
            route = NavRoutes.TRANSITION,
            arguments = listOf(
                navArgument(NavRoutes.SESSION_ID) { type = NavType.StringType },
                navArgument(NavRoutes.MOVIE_ID) { type = NavType.IntType },
                navArgument(NavRoutes.TO_ACTOR_ID) { type = NavType.IntType },
            ),
        ) { entry ->
            TransitionScreen(
                navController = navController,
                sessionId = entry.requireSessionId(),
                movieId = entry.requireMovieId(),
                toActorId = entry.requireToActorId(),
            )
        }
        composable(
            route = NavRoutes.RESULT_WON,
            arguments = listOf(navArgument(NavRoutes.SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            GameResultScreen(navController, entry.requireSessionId())
        }
        composable(
            route = NavRoutes.RESULT_NON_WIN,
            arguments = listOf(navArgument(NavRoutes.SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            GameResultNonWinScreen(navController, entry.requireSessionId())
        }
        composable(
            route = NavRoutes.CHAIN,
            arguments = listOf(navArgument(NavRoutes.SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            ChainScreen(navController, entry.requireSessionId())
        }
        composable(
            route = NavRoutes.ERROR,
            arguments = listOf(
                navArgument(NavRoutes.ERROR_TYPE) {
                    defaultValue = ErrorType.UNKNOWN.route
                },
            ),
        ) { ErrorScreen(navController) }
    }
}

private fun androidx.navigation.NavBackStackEntry.requireSessionId(): String =
    requireNotNull(arguments?.getString(NavRoutes.SESSION_ID)) {
        "Маршрут без аргумента ${NavRoutes.SESSION_ID}"
    }

private fun androidx.navigation.NavBackStackEntry.requireMovieId(): Int =
    requireNotNull(arguments?.getInt(NavRoutes.MOVIE_ID)) {
        "Маршрут без аргумента ${NavRoutes.MOVIE_ID}"
    }

private fun androidx.navigation.NavBackStackEntry.requireToActorId(): Int =
    requireNotNull(arguments?.getInt(NavRoutes.TO_ACTOR_ID)) {
        "Маршрут без аргумента ${NavRoutes.TO_ACTOR_ID}"
    }
