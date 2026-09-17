package com.colorzen.puzzle.core

import com.colorzen.puzzle.game.LevelPlan

/**
 * Single place for the values Google Play asks you to declare.
 *
 * PRIVACY_POLICY_URL is the only value you are likely to need to change: Play
 * Console requires a *live*, publicly reachable URL. The default below works as
 * soon as GitHub Pages is enabled for this repository (Settings -> Pages ->
 * Deploy from a branch -> main / root), because `privacy.html` ships at the
 * repository root. See docs/PUBLISHING.md.
 */
object AppConfig {

    const val APPLICATION_ID = "com.colorzen.puzzle"
    const val VERSION_NAME = "1.0.0"
    const val VERSION_CODE = 1

    /** Must stay in sync with the URL entered in Play Console. */
    const val PRIVACY_POLICY_URL =
        "https://mohammedalhinaki-cloud.github.io/Color-Zen/privacy.html"

    /** Shown in Settings and used for the Play Console support contact. */
    const val SUPPORT_EMAIL = "support@colorzen.app"

    const val TOTAL_LEVELS = LevelPlan.TOTAL_LEVELS
    const val FREE_LEVELS = LevelPlan.FREE_LEVELS

    /** Hints a brand-new player starts with, so the mechanic is discoverable. */
    const val STARTING_HINTS = 3

    /** Hints are always free to *receive* the first time a level is completed. */
    const val HINT_COST_COINS = 1

    /**
     * The app declares and collects no personal data: no analytics, no crash
     * reporting, no accounts, no advertising id, and no network permission at
     * all. Play Billing talks to the Play Store over binder, not the network.
     * Keep this list empty - it is the evidence for the Data Safety form.
     */
    val dataCollected: List<String> = emptyList()

    /** COPPA: the app is directed at everyone, including children. */
    const val CHILD_DIRECTED = true
}
