package com.colorzen.puzzle.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-device hint engine. It must agree with [GameRules] (every returned
 * move is legal) and must cope with the hardest shipped configuration:
 * 13 bottles / 11 colours / 2 spares (levels 106-120).
 */
class SolverTest {

    private fun bottle(vararg colours: Int): List<Int> = colours.toList()

    private fun replay(state: List<List<Int>>, moves: List<GameRules.Move>): List<List<Int>> =
        moves.fold(state) { acc, move -> GameRules.apply(acc, move) }

    @Test
    fun `already solved state needs no moves`() {
        val solved = listOf(bottle(), bottle(1, 1, 1, 1), bottle(2, 2, 2, 2))
        assertEquals(emptyList<GameRules.Move>(), Solver.solve(solved))
    }

    @Test
    fun `solution replays into a solved board`() {
        val state = listOf(
            bottle(1, 2, 1, 2),
            bottle(2, 1, 2, 1),
            bottle(),
        )
        val solution = Solver.solve(state)
        assertNotNull(solution)
        assertTrue(GameRules.isSolved(replay(state, solution!!)))
    }

    @Test
    fun `every step of a solution is a legal move`() {
        val state = listOf(
            bottle(1, 2, 3, 1),
            bottle(2, 3, 1, 2),
            bottle(3, 1, 2, 3),
            bottle(),
        )
        var current = state
        for (move in Solver.solve(state)!!) {
            assertTrue(GameRules.canPour(current, move.from, move.to))
            current = GameRules.apply(current, move)
        }
        assertTrue(GameRules.isSolved(current))
    }

    @Test
    fun `dead end has no moves and no hint`() {
        val dead = listOf(bottle(1, 2, 1, 2), bottle(2, 1, 2, 1))
        assertFalse(Solver.hasAnyMove(dead))
        assertNull(Solver.solve(dead))
        val hint = Solver.hint(dead)
        assertNull(hint.move)
        assertFalse(hint.exactSolution)
    }

    @Test
    fun `hint move is legal and marked exact when solvable`() {
        val state = listOf(
            bottle(1, 2, 2, 1),
            bottle(2, 1, 1, 2),
            bottle(),
        )
        val hint = Solver.hint(state)
        assertNotNull(hint.move)
        assertTrue(hint.exactSolution)
        assertTrue(GameRules.canPour(state, hint.move!!))
    }

    @Test
    fun `next move leads to a solvable position`() {
        val state = listOf(
            bottle(1, 2, 3, 1),
            bottle(2, 3, 1, 2),
            bottle(3, 1, 2, 3),
            bottle(),
        )
        val move = Solver.nextMove(state)
        assertNotNull(move)
        val after = GameRules.apply(state, move!!)
        assertNotNull(Solver.solve(after))
    }

    @Test
    fun `hardest shipped configuration solves inside the hint budget`() {
        // Levels 106-120: 11 colours, 11 filled bottles, 2 spares.
        val state = listOf(
            bottle(1, 2, 3, 4),
            bottle(5, 6, 7, 8),
            bottle(9, 10, 11, 1),
            bottle(2, 3, 4, 5),
            bottle(6, 7, 8, 9),
            bottle(10, 11, 1, 2),
            bottle(3, 4, 5, 6),
            bottle(7, 8, 9, 10),
            bottle(11, 1, 2, 3),
            bottle(4, 5, 6, 7),
            bottle(8, 9, 10, 11),
            bottle(),
            bottle(),
        )
        val start = System.nanoTime()
        val solution = Solver.solve(state, Solver.DEFAULT_BUDGET)
        val millis = (System.nanoTime() - start) / 1_000_000
        assertNotNull("master level must be solvable", solution)
        assertTrue(GameRules.isSolved(replay(state, solution!!)))
        assertTrue("hint must be fast on device, took ${millis}ms", millis < 2000)
    }

    @Test
    fun `tiny budget gives up instead of hanging`() {
        val state = listOf(
            bottle(1, 2, 3, 4),
            bottle(2, 3, 4, 1),
            bottle(3, 4, 1, 2),
            bottle(4, 1, 2, 3),
            bottle(),
        )
        // With a budget of one node the search cannot finish; it must return
        // null rather than loop forever.
        assertNull(Solver.solve(state, budget = 1))
    }

    @Test
    fun `fallback hint prefers a finishing pour`() {
        val state = listOf(
            bottle(5, 2, 2),
            bottle(2, 2),
            bottle(3, 4),
        )
        val move = Solver.fallbackHint(state)
        assertEquals(GameRules.Move(0, 1, 2), move)
    }
}
