package com.zurus.kinosvyazi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zurus.kinosvyazi.feature.actorpicker.ActorPickerScreen
import com.zurus.kinosvyazi.feature.chain.ChainScreen
import com.zurus.kinosvyazi.feature.error.ErrorScreen
import com.zurus.kinosvyazi.feature.game.GameScreen
import com.zurus.kinosvyazi.feature.game.GameSetupScreen
import com.zurus.kinosvyazi.feature.home.HomeScreen
import com.zurus.kinosvyazi.feature.moviepicker.MoviePickerScreen
import com.zurus.kinosvyazi.feature.result.GameResultNonWinScreen
import com.zurus.kinosvyazi.feature.result.GameResultScreen
import com.zurus.kinosvyazi.feature.rules.RulesScreen
import com.zurus.kinosvyazi.feature.transition.TransitionScreen

/**
 * Граф навигации приложения (docs/01_concept.md, §2).
 *
 * На Этапе 1 все экраны — composable-заглушки;
 * логика экранов появляется на Этапах 4–5 (docs/05_roadmap.md).
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
    ) {
        composable(NavRoutes.HOME) { HomeScreen() }
        composable(NavRoutes.RULES) { RulesScreen() }
        composable(NavRoutes.GAME_SETUP) { GameSetupScreen() }
        composable(NavRoutes.GAME) { GameScreen() }
        composable(NavRoutes.MOVIE_PICKER) { MoviePickerScreen() }
        composable(NavRoutes.ACTOR_PICKER) { ActorPickerScreen() }
        composable(NavRoutes.TRANSITION) { TransitionScreen() }
        composable(NavRoutes.RESULT_WON) { GameResultScreen() }
        composable(NavRoutes.RESULT_NON_WIN) { GameResultNonWinScreen() }
        composable(NavRoutes.CHAIN) { ChainScreen() }
        composable(NavRoutes.ERROR) { ErrorScreen() }
    }
}
