package com.colorzen.puzzle.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Grid math for the board canvas. Pure Kotlin (no Android types) so the layout
 * and thumb-friendly hit testing are locked down by JVM tests.
 */
class BoardGeometryTest {

    private val width = 1000f
    private val height = 1400f

    @Test
    fun `small boards use a single row`() {
        val g = BoardGeometry(4, width, height)
        assertEquals(1, g.rows)
        assertEquals(4, g.columns)
        assertEquals(4, g.cells.size)
    }

    @Test
    fun `seven bottles form a balanced four column grid`() {
        val g = BoardGeometry(7, width, height)
        assertEquals(2, g.rows)
        assertEquals(4, g.columns)
        assertEquals(7, g.cells.size)
    }

    @Test
    fun `thirteen bottles use three rows of at most five`() {
        val g = BoardGeometry(13, width, height)
        assertEquals(3, g.rows)
        assertEquals(5, g.columns)
        assertEquals(13, g.cells.size)
    }

    @Test
    fun `never exceeds the column cap`() {
        for (count in 1..13) {
            val g = BoardGeometry(count, width, height)
            assertTrue("columns=${g.columns} for count=$count", g.columns <= 5)
            assertTrue(g.rows * g.columns >= count)
        }
    }

    @Test
    fun `short final row is centred`() {
        val g = BoardGeometry(7, width, height)
        val lastRow = g.cells.drop(4)
        assertEquals(3, lastRow.size)
        val mean = lastRow.map { it.centerX }.average()
        assertTrue(abs(mean - width / 2) < 1.0)
        // and the first row spans the full width
        val firstRow = g.cells.take(4)
        assertTrue(firstRow[0].centerX < lastRow[0].centerX)
    }

    @Test
    fun `all cells stay inside the board`() {
        for (count in 1..13) {
            val g = BoardGeometry(count, width, height)
            for (cell in g.cells) {
                assertTrue(cell.left >= -0.5f)
                assertTrue(cell.top >= -0.5f)
                assertTrue(cell.right <= width + 0.5f)
                assertTrue(cell.bottom <= height + 0.5f)
                assertTrue(cell.width > 0f)
                assertTrue(cell.height > 0f)
            }
        }
    }

    @Test
    fun `bottles keep a constant aspect ratio`() {
        val g = BoardGeometry(6, width, height)
        assertEquals(BoardGeometry.DEFAULT_ASPECT, g.bottleWidth / g.bottleHeight, 0.01f)
        for (cell in g.cells) {
            assertEquals(g.bottleWidth, cell.width, 0.01f)
            assertEquals(g.bottleHeight, cell.height, 0.01f)
        }
    }

    @Test
    fun `touch at a cell centre resolves to that bottle`() {
        val g = BoardGeometry(9, width, height)
        for (index in g.cells.indices) {
            val cell = g.cells[index]
            assertEquals(index, g.indexAt(cell.centerX, cell.centerY))
        }
    }

    @Test
    fun `touch slightly outside a bottle still resolves to it`() {
        val g = BoardGeometry(5, width, height)
        val cell = g.cells[2]
        val slop = maxOf(12f, g.bottleWidth * 0.18f)
        assertEquals(2, g.indexAt(cell.left - slop * 0.5f, cell.centerY))
        assertEquals(2, g.indexAt(cell.centerX, cell.top - slop * 0.5f))
    }

    @Test
    fun `touch far away resolves to nothing`() {
        val g = BoardGeometry(5, width, height)
        assertEquals(-1, g.indexAt(-500f, -500f))
        assertEquals(-1, g.indexAt(width * 2f, height * 2f))
    }

    @Test
    fun `segments stack from the bottom`() {
        val g = BoardGeometry(4, width, height)
        val outer = g.cells[0]
        val wall = 6f
        val innerTop = outer.top + wall
        val innerBottom = outer.bottom - wall
        val step = (innerBottom - innerTop) / 4f

        val bottom = g.segmentRect(outer, wall, 0, 4)
        assertEquals(innerBottom, bottom.bottom, 0.01f)
        assertEquals(step, bottom.height, 0.01f)

        val top = g.segmentRect(outer, wall, 3, 4)
        assertEquals(innerTop, top.top, 0.01f)

        // consecutive segments tile without gaps
        for (s in 0..2) {
            val a = g.segmentRect(outer, wall, s, 4)
            val b = g.segmentRect(outer, wall, s + 1, 4)
            assertEquals(b.bottom, a.top, 0.01f)
        }
    }

    @Test
    fun `surface rises with fill level`() {
        val g = BoardGeometry(4, width, height)
        val outer = g.cells[0]
        val wall = 6f
        assertEquals(outer.bottom - wall, g.surfaceY(outer, wall, 0, 4), 0.01f)
        assertEquals(outer.top + wall, g.surfaceY(outer, wall, 4, 4), 0.01f)
        val one = g.surfaceY(outer, wall, 1, 4)
        val two = g.surfaceY(outer, wall, 2, 4)
        assertTrue(two < one)
    }
}
