package com.colorzen.puzzle.core

import com.colorzen.puzzle.billing.ProductIds
import com.colorzen.puzzle.game.LevelPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Monetisation gating rules. These decide what a player may open, so they are
 * pinned down by tests: free tier, sequential progression, per-pack ownership
 * and the Full Game override.
 */
class EntitlementsTest {

    private val nothing = emptySet<String>()

    @Test
    fun `first level is always open`() {
        assertTrue(Entitlements.isLevelUnlocked(nothing, emptySet(), 1))
    }

    @Test
    fun `free tier plays without purchase but sequentially`() {
        assertTrue(Entitlements.isLevelUnlocked(nothing, setOf(1), 2))
        assertTrue(Entitlements.isLevelUnlocked(nothing, (1..29).toSet(), 30))
        assertFalse(Entitlements.isLevelUnlocked(nothing, emptySet(), 2))
        assertFalse(Entitlements.isLevelUnlocked(nothing, (1..29).toSet(), 31))
    }

    @Test
    fun `level 31 needs pack 1`() {
        val completed = (1..30).toSet()
        assertFalse(Entitlements.isLevelUnlocked(nothing, completed, 31))
        assertTrue(
            Entitlements.isLevelUnlocked(setOf(ProductIds.LEVEL_PACK_1), completed, 31),
        )
        assertEquals(
            LevelLock.PackRequired(1),
            Entitlements.lockReason(nothing, completed, 31),
        )
        assertEquals(
            LevelLock.Unlocked,
            Entitlements.lockReason(setOf(ProductIds.LEVEL_PACK_1), completed, 31),
        )
    }

    @Test
    fun `owning a pack does not skip the previous level`() {
        val owned = setOf(ProductIds.LEVEL_PACK_1)
        assertFalse(Entitlements.isLevelUnlocked(owned, (1..29).toSet(), 31))
        assertEquals(
            LevelLock.PreviousIncomplete,
            Entitlements.lockReason(owned, (1..29).toSet(), 31),
        )
    }

    @Test
    fun `full game supersedes every pack`() {
        val owned = setOf(ProductIds.FULL_GAME)
        assertTrue(Entitlements.ownsFullGame(owned))
        for (pack in 1..3) assertTrue(Entitlements.ownsPack(owned, pack))
        val completed = (1..119).toSet()
        assertTrue(Entitlements.isLevelUnlocked(owned, completed, 120))
    }

    @Test
    fun `pack ownership is per pack`() {
        val owned = setOf(ProductIds.LEVEL_PACK_2)
        assertFalse(Entitlements.ownsPack(owned, 1))
        assertTrue(Entitlements.ownsPack(owned, 2))
        assertFalse(Entitlements.ownsPack(owned, 3))
        // pack 0 is the free starter set
        assertTrue(Entitlements.ownsPack(nothing, 0))
    }

    @Test
    fun `unknown or out of range packs are never owned`() {
        assertFalse(Entitlements.ownsPack(setOf("nonsense"), 9))
        assertFalse(Entitlements.isLevelUnlocked(nothing, (1..119).toSet(), 121))
        assertFalse(Entitlements.isLevelUnlocked(nothing, emptySet(), 0))
    }

    @Test
    fun `themes are a separate purchase`() {
        assertFalse(Entitlements.ownsThemes(nothing))
        assertTrue(Entitlements.ownsThemes(setOf(ProductIds.COLOR_THEMES)))
        // owning themes must not unlock levels
        assertFalse(
            Entitlements.isLevelUnlocked(setOf(ProductIds.COLOR_THEMES), (1..30).toSet(), 31),
        )
    }

    @Test
    fun `next playable level is the first unfinished one`() {
        assertEquals(1, Entitlements.nextPlayableLevel(emptySet()))
        assertEquals(7, Entitlements.nextPlayableLevel((1..6).toSet()))
        assertEquals(
            LevelPlan.TOTAL_LEVELS,
            Entitlements.nextPlayableLevel((1..LevelPlan.TOTAL_LEVELS).toSet()),
        )
    }
}
