package com.zurus.kinosvyazi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zurus.kinosvyazi.feature.actorpicker.ActorPickerScreen
import com.zurus.kinosvyazi.feature.chain.ChainScreen
import com.zurus.kinosvyazi.feature.error.ErrorScreen
import com.zurus.kinosvyazi.feature.error.ErrorType
import com.zurus.kinosvyazi.feature.game.GameScreen
import com.zurus.kinosvyazi.feature.game.GameSetupScreen
import com.zurus.kinosvyazi.feature.home.HomeScreen
import com.zurus.kinosvyazi.feature.moviepicker.MoviePickerScreen
import com.zurus.kinosvyazi.feature.result.GameResultNonWinScreen
import com.zurus.kinosvyazi.feature.result.GameResultScreen
import com.zurus.kinosvyazi.feature.rules.RulesScreen
import com.zurus.kinosvyazi.feature.transition.TransitionScreen

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
        composable(NavRoutes.GAME) { entry ->
            GameScreen(navController, entry.requireSessionId())
        }
        composable(NavRoutes.MOVIE_PICKER) { entry ->
            MoviePickerScreen(navController, entry.requireSessionId())
        }
        composable(NavRoutes.ACTOR_PICKER) { entry ->
            ActorPickerScreen(
                navController = navController,
                sessionId = entry.requireSessionId(),
                movieId = entry.requireMovieId(),
            )
        }
        composable(NavRoutes.TRANSITION) { entry ->
            TransitionScreen(
                navController = navController,
                sessionId = entry.requireSessionId(),
                movieId = entry.requireMovieId(),
                toActorId = entry.arguments?.getInt(NavRoutes.TO_ACTOR_ID) ?: 0,
            )
        }
        composable(NavRoutes.RESULT_WON) { entry ->
            GameResultScreen(navController, entry.requireSessionId())
        }
        composable(NavRoutes.RESULT_NON_WIN) { entry ->
            GameResultNonWinScreen(navController, entry.requireSessionId())
        }
        composable(NavRoutes.CHAIN) { entry ->
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
