package com.colorzen.puzzle.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The complete water-sort rule set. These are pure JVM tests: [GameRules] has
 * no Android dependency, and `tools/generate_levels.py` mirrors these exact
 * rules when it proves every shipped level solvable.
 */
class GameRulesTest {

    private fun bottle(vararg colours: Int): List<Int> = colours.toList()

    private fun state(vararg bottles: List<Int>): List<List<Int>> = bottles.toList()

    // -- topRun / freeSpace --------------------------------------------------

    @Test
    fun `top run of empty bottle is zero`() {
        assertEquals(0 to 0, GameRules.topRun(bottle()))
    }

    @Test
    fun `top run counts only the contiguous top segment`() {
        assertEquals(2 to 2, GameRules.topRun(bottle(1, 3, 2, 2)))
        assertEquals(3 to 1, GameRules.topRun(bottle(2, 2, 1, 3)))
        assertEquals(5 to 3, GameRules.topRun(bottle(5, 5, 5)))
    }

    @Test
    fun `free space is capacity minus fill`() {
        assertEquals(4, GameRules.freeSpace(bottle()))
        assertEquals(1, GameRules.freeSpace(bottle(1, 2, 3)))
        assertEquals(0, GameRules.freeSpace(bottle(1, 2, 3, 4)))
    }

    // -- pour legality -------------------------------------------------------

    @Test
    fun `pour needs matching top colours`() {
        val s = state(bottle(1, 2), bottle(3), bottle())
        assertEquals(0, GameRules.pourCount(s, 0, 1))
        // an empty destination always accepts
        assertEquals(1, GameRules.pourCount(s, 0, 2))
        assertEquals(1, GameRules.pourCount(s, 1, 2))
    }

    @Test
    fun `pour is limited by destination free space`() {
        val s = state(bottle(3, 3, 3), bottle(3))
        assertEquals(3, GameRules.pourCount(s, 0, 1))
    }

    @Test
    fun `pour into a full bottle is illegal`() {
        val s = state(bottle(1, 1), bottle(2, 2, 2, 2))
        assertEquals(0, GameRules.pourCount(s, 0, 1))
    }

    @Test
    fun `pouring onto itself is illegal`() {
        val s = state(bottle(1, 2), bottle())
        assertEquals(0, GameRules.pourCount(s, 0, 0))
    }

    @Test
    fun `out of range indices are illegal`() {
        val s = state(bottle(1), bottle())
        assertEquals(0, GameRules.pourCount(s, 0, 7))
        assertEquals(0, GameRules.pourCount(s, -1, 0))
    }

    // -- applying moves ------------------------------------------------------

    @Test
    fun `apply moves the whole top run when it fits`() {
        val s = state(bottle(1, 2, 2), bottle(2), bottle())
        val next = GameRules.apply(s, GameRules.Move(0, 1, 4))
        assertEquals(state(bottle(1), bottle(2, 2, 2)), next)
    }

    @Test
    fun `apply pours only as much as fits`() {
        val s = state(bottle(4, 4, 4), bottle(4))
        val next = GameRules.apply(s, GameRules.Move(0, 1, 4))
        assertEquals(state(bottle(), bottle(4, 4, 4, 4)), next)
    }

    @Test
    fun `invalid move leaves state untouched`() {
        val s = state(bottle(1, 2), bottle(3))
        assertEquals(s, GameRules.apply(s, GameRules.Move(0, 1, 2)))
    }

    @Test
    fun `apply respects an explicit smaller count`() {
        val s = state(bottle(1, 2, 2), bottle(2, 2))
        val next = GameRules.apply(s, GameRules.Move(0, 1, 1))
        assertEquals(state(bottle(1, 2), bottle(2, 2, 2)), next)
    }

    // -- solved detection ----------------------------------------------------

    @Test
    fun `solved when every bottle is empty or single coloured and full`() {
        assertTrue(GameRules.isSolved(state(bottle(), bottle(2, 2, 2, 2), bottle(1, 1, 1, 1))))
        assertFalse(GameRules.isSolved(state(bottle(1, 1, 1), bottle(2, 2, 2, 2))))
        assertFalse(GameRules.isSolved(state(bottle(1, 2, 1, 1))))
    }

    @Test
    fun `completed bottles reports only finished bottles`() {
        val s = state(bottle(3, 3, 3, 3), bottle(1, 1, 1), bottle())
        assertEquals(setOf(0), GameRules.completedBottles(s))
    }

    // -- move generation -----------------------------------------------------

    @Test
    fun `legal moves never include no-op relocations of pure bottles`() {
        val s = state(bottle(1, 1), bottle(), bottle(2, 3))
        val moves = GameRules.legalMoves(s)
        assertFalse(moves.any { it.from == 0 && it.to == 1 })
        assertTrue(moves.isNotEmpty())
    }

    @Test
    fun `completing pours are ordered first`() {
        // 0 -> 1 finishes bottle 1; 0 -> 2 is an ordinary pour.
        val s = state(bottle(5, 2, 2), bottle(2, 2), bottle())
        val moves = GameRules.legalMoves(s)
        assertEquals(GameRules.Move(0, 1, 2), moves.first())
    }

    @Test
    fun `legal moves are all individually legal`() {
        val s = state(bottle(1, 2, 3, 4), bottle(4, 3, 2, 1), bottle(), bottle(2, 1, 4, 3))
        for (move in GameRules.legalMoves(s)) {
            assertTrue(GameRules.canPour(s, move.from, move.to))
            assertEquals(move.count, GameRules.pourCount(s, move.from, move.to))
        }
    }

    // -- canonicalisation ----------------------------------------------------

    @Test
    fun `canonical ignores bottle order`() {
        val a = state(bottle(1, 2), bottle(3), bottle())
        val b = state(bottle(), bottle(3), bottle(1, 2))
        assertEquals(GameRules.canonical(a), GameRules.canonical(b))
        assertEquals(GameRules.encode(a), GameRules.encode(b))
    }

    @Test
    fun `encode distinguishes different states`() {
        val a = state(bottle(1, 2), bottle(3))
        val b = state(bottle(2, 1), bottle(3))
        assertNotEquals(GameRules.encode(a), GameRules.encode(b))
    }

    @Test
    fun `encode handles two digit colours`() {
        val key = GameRules.encode(state(bottle(10, 11), bottle()))
        assertTrue(key.contains("ab|"))
    }

    // -- invariants ----------------------------------------------------------

    @Test
    fun `colour histogram is preserved by every legal move`() {
        val s = state(bottle(1, 2, 3, 4), bottle(4, 3, 2, 1), bottle(), bottle(2, 1, 4, 3))
        val before = GameRules.colourCounts(s)
        for (move in GameRules.legalMoves(s)) {
            assertEquals(before, GameRules.colourCounts(GameRules.apply(s, move)))
        }
    }
}
