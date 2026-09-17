package com.colorzen.puzzle.core

import com.colorzen.puzzle.billing.ProductIds
import com.colorzen.puzzle.game.LevelPlan

/**
 * Pure entitlement rules: given the set of owned product ids and the set of
 * completed levels, what may the player do?
 *
 * Kept as free functions over plain data so that
 *  * screens can call them with values read through `collectAsState()` (which is
 *    what makes the UI update the moment a purchase lands), and
 *  * the rules are covered by JVM unit tests with no Android dependencies.
 */
object Entitlements {

    fun ownsFullGame(owned: Set<String>): Boolean = ProductIds.FULL_GAME in owned

    fun ownsThemes(owned: Set<String>): Boolean = ProductIds.COLOR_THEMES in owned

    /** The first 30 levels are free; later ones need their pack or Full Game. */
    fun ownsPack(owned: Set<String>, packIndex: Int): Boolean {
        if (packIndex <= 0) return true
        if (ownsFullGame(owned)) return true
        val productId = ProductIds.levelPacks.getOrNull(packIndex - 1) ?: return false
        return productId in owned
    }

    /**
     * A level is playable when its pack is owned *and* the previous level has
     * been completed (sequential progression, the norm for this genre).
     */
    fun isLevelUnlocked(owned: Set<String>, completed: Set<Int>, level: Int): Boolean {
        if (level < 1) return false
        if (level == 1) return true
        if (level > LevelPlan.TOTAL_LEVELS) return false
        if (!ownsPack(owned, LevelPlan.packIndexFor(level))) return false
        return (level - 1) in completed
    }

    fun lockReason(owned: Set<String>, completed: Set<Int>, level: Int): LevelLock {
        if (isLevelUnlocked(owned, completed, level)) return LevelLock.Unlocked
        val packIndex = LevelPlan.packIndexFor(level)
        return if (!ownsPack(owned, packIndex)) {
            LevelLock.PackRequired(packIndex)
        } else {
            LevelLock.PreviousIncomplete
        }
    }

    /** First unfinished level, or the last level once everything is done. */
    fun nextPlayableLevel(completed: Set<Int>): Int =
        (1..LevelPlan.TOTAL_LEVELS).firstOrNull { it !in completed } ?: LevelPlan.TOTAL_LEVELS
}
