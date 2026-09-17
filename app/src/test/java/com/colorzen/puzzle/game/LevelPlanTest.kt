package com.colorzen.puzzle.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipped campaign: 120 levels, 30 free, three paid packs of 30, and the
 * difficulty curve that the generator in tools/generate_levels.py produced.
 */
class LevelPlanTest {

    @Test
    fun `campaign shape matches the store promise`() {
        assertEquals(120, LevelPlan.TOTAL_LEVELS)
        assertEquals(30, LevelPlan.FREE_LEVELS)
        assertEquals(3, LevelPlan.PACK_COUNT)
        assertEquals(1..30, LevelPlan.levelsOfPack(0))
        assertEquals(31..60, LevelPlan.levelsOfPack(1))
        assertEquals(61..90, LevelPlan.levelsOfPack(2))
        assertEquals(91..120, LevelPlan.levelsOfPack(3))
    }

    @Test
    fun `pack index covers every level exactly once`() {
        for (level in 1..LevelPlan.TOTAL_LEVELS) {
            val pack = LevelPlan.packIndexFor(level)
            assertTrue(pack in 0..3)
            if (pack > 0) assertTrue(level in LevelPlan.levelsOfPack(pack))
        }
        assertEquals(0, LevelPlan.packIndexFor(30))
        assertEquals(1, LevelPlan.packIndexFor(31))
        assertEquals(1, LevelPlan.packIndexFor(60))
        assertEquals(2, LevelPlan.packIndexFor(61))
        assertEquals(3, LevelPlan.packIndexFor(120))
    }

    @Test
    fun `difficulty grows monotonically`() {
        var previousBottles = 0
        var previousColors = 0
        for (level in 1..LevelPlan.TOTAL_LEVELS) {
            val bottles = LevelPlan.bottlesFor(level)
            val colors = LevelPlan.colorsFor(level)
            assertTrue("level $level bottles", bottles >= previousBottles)
            assertTrue("level $level colors", colors >= previousColors)
            previousBottles = bottles
            previousColors = colors
            // every colour must fit: filled bottles = colours, plus spares
            assertTrue(bottles > colors)
            assertTrue(colors <= 11)
        }
    }

    @Test
    fun `starter tiers keep one spare bottle, paid tiers two`() {
        assertEquals(1, LevelPlan.blockFor(1).spareBottles)
        assertEquals(1, LevelPlan.blockFor(30).spareBottles)
        assertEquals(2, LevelPlan.blockFor(31).spareBottles)
        assertEquals(2, LevelPlan.blockFor(120).spareBottles)
    }

    @Test
    fun `blocks tile the campaign without gaps or overlaps`() {
        var expected = 1
        for (block in LevelPlan.blocks) {
            assertEquals(expected, block.fromLevel)
            expected = block.toLevel + 1
        }
        assertEquals(LevelPlan.TOTAL_LEVELS + 1, expected)
    }

    @Test
    fun `sections follow the tier order`() {
        assertEquals(6, LevelPlan.sections.size)
        assertEquals(Tier.BEGINNER, LevelPlan.sections.first().tier)
        assertEquals(Tier.MASTER, LevelPlan.sections.last().tier)
    }

    @Test
    fun `star bands are generous but honest`() {
        assertEquals(3, LevelPlan.starsFor(10, 10))
        assertEquals(3, LevelPlan.starsFor(12, 10))
        assertEquals(2, LevelPlan.starsFor(13, 10))
        assertEquals(2, LevelPlan.starsFor(17, 10))
        assertEquals(1, LevelPlan.starsFor(18, 10))
        assertEquals(1, LevelPlan.starsFor(99, 0))
    }
}
