package com.colorzen.puzzle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.colorzen.puzzle.core.appContainer
import com.colorzen.puzzle.ui.ColorZenApp
import com.colorzen.puzzle.ui.theme.ColorZenTheme

/**
 * Single-activity host.
 *
 * `enableEdgeToEdge()` is required for a good result on Android 15/16, where
 * edge-to-edge is enforced for apps targeting SDK 35+: every screen consumes
 * window insets explicitly instead of relying on system bar padding.
 *
 * Play Billing is connected here (not in the Application) because the store is
 * only needed once there is a UI to show prices in, and connecting early means
 * the shop is already populated when the player reaches it.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = appContainer
        container.billingManager.start()

        setContent {
            ColorZenTheme {
                ColorZenApp(container = container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Prices and entitlements can change while the app is backgrounded
        // (e.g. a purchase completed from the Play Store notification shade).
        appContainer.billingManager.refresh()
    }
}
