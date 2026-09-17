package com.colorzen.puzzle.core

import android.app.Application
import android.content.Context
import com.colorzen.puzzle.billing.BillingManager
import com.colorzen.puzzle.game.LevelRepository

/**
 * Tiny hand-rolled dependency container.
 *
 * The app has four long-lived collaborators; a DI framework would be more risk
 * and more build time than the problem deserves.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val playerStore: PlayerStore by lazy { PlayerStore(appContext) }

    val soundManager: SoundManager by lazy { SoundManager(appContext, playerStore) }

    val levelRepository: LevelRepository by lazy { LevelRepository(appContext) }

    val billingManager: BillingManager by lazy { BillingManager(appContext, playerStore) }
}

class ColorZenApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.soundManager.init()
    }

    override fun onTerminate() {
        container.soundManager.release()
        container.billingManager.release()
        super.onTerminate()
    }
}

/** Convenience accessor for ViewModels and composables. */
val Context.appContainer: AppContainer
    get() = (applicationContext as ColorZenApplication).container
