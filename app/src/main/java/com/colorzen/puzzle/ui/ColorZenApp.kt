package com.colorzen.puzzle.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.colorzen.puzzle.core.AppContainer
import com.colorzen.puzzle.ui.game.GameScreen
import com.colorzen.puzzle.ui.levels.LevelSelectScreen
import com.colorzen.puzzle.ui.menu.MenuScreen
import com.colorzen.puzzle.ui.nav.Routes
import com.colorzen.puzzle.ui.nav.ZenNavigator
import com.colorzen.puzzle.ui.privacy.PrivacyPolicyScreen
import com.colorzen.puzzle.ui.settings.SettingsScreen
import com.colorzen.puzzle.ui.shop.ShopScreen

/**
 * The whole app: one [NavHost] with the seven screens from the product spec.
 *
 *  1. Main Menu
 *  2. Level Selection (locked / unlocked)
 *  3. Gameplay
 *  4. Level Complete (rendered by [GameScreen] as a full-screen animated panel
 *     over the solved board, so the completion animation is continuous)
 *  5. Shop
 *  6. Settings (sound effects toggle)
 *  7. Privacy Policy
 */
@Composable
fun ColorZenApp(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navigator = remember(navController) { ZenNavigator(navController, container.soundManager) }

    NavHost(
        navController = navController,
        startDestination = Routes.MENU,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.MENU) {
            MenuScreen(container = container, navigator = navigator)
        }

        composable(Routes.LEVELS) {
            LevelSelectScreen(container = container, navigator = navigator)
        }

        composable(
            route = Routes.GAME_PATTERN,
            arguments = listOf(navArgument(Routes.GAME_ARG) { type = NavType.IntType }),
        ) { entry ->
            val level = entry.arguments?.getInt(Routes.GAME_ARG) ?: 1
            GameScreen(
                levelNumber = level,
                container = container,
                navigator = navigator,
            )
        }

        composable(Routes.SHOP) {
            ShopScreen(container = container, navigator = navigator)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, navigator = navigator)
        }

        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(container = container, navigator = navigator)
        }
    }
}
