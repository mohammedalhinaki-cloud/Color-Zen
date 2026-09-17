package com.colorzen.puzzle.ui.nav

import androidx.navigation.NavHostController
import com.colorzen.puzzle.core.SoundManager
import com.colorzen.puzzle.core.Sfx

/** Route names. String routes keep this navigation graph boring and stable. */
object Routes {
    const val MENU = "menu"
    const val LEVELS = "levels"
    const val SHOP = "shop"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    const val GAME_ARG = "level"
    const val GAME_PATTERN = "game/{$GAME_ARG}"

    fun game(level: Int) = "game/$level"
}

/**
 * Navigation + the button-click sound in one place, so no screen can forget to
 * play the click effect and no screen needs to know about routes.
 */
class ZenNavigator(
    private val navController: NavHostController,
    private val sound: SoundManager,
) {

    /** Audible feedback for any non-navigating control. */
    fun click() = sound.play(Sfx.CLICK)

    fun toMenu() {
        click()
        navController.navigate(Routes.MENU) {
            popUpTo(Routes.MENU) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun toLevels() {
        click()
        navController.navigate(Routes.LEVELS) { launchSingleTop = true }
    }

    fun toGame(level: Int) {
        click()
        navController.navigate(Routes.game(level)) { launchSingleTop = true }
    }

    /** Next level after a win: replaces the current game entry in the stack. */
    fun toNextLevel(level: Int) {
        click()
        navController.navigate(Routes.game(level)) {
            popUpTo(Routes.GAME_PATTERN) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun toShop() {
        click()
        navController.navigate(Routes.SHOP) { launchSingleTop = true }
    }

    fun toSettings() {
        click()
        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
    }

    fun toPrivacy() {
        click()
        navController.navigate(Routes.PRIVACY) { launchSingleTop = true }
    }

    fun back() {
        click()
        if (!navController.popBackStack()) {
            navController.navigate(Routes.MENU) { launchSingleTop = true }
        }
    }
}
