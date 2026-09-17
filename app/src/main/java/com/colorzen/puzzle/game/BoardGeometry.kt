package com.colorzen.puzzle.game

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * An axis-aligned rectangle in board coordinates (pixels, origin top-left).
 *
 * Deliberately a plain data class instead of `androidx.compose.ui.geometry.Rect`
 * so the board maths can be covered by JVM unit tests with no Compose runtime.
 */
data class CellRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun inset(horizontal: Float, vertical: Float): CellRect = CellRect(
        left = left + horizontal,
        top = top + vertical,
        right = right - horizontal,
        bottom = bottom - vertical,
    )

    fun expandedBy(amount: Float): CellRect = CellRect(
        left = left - amount,
        top = top - amount,
        right = right + amount,
        bottom = bottom + amount,
    )

    fun translatedBy(dx: Float, dy: Float): CellRect = CellRect(
        left = left + dx,
        top = top + dy,
        right = right + dx,
        bottom = bottom + dy,
    )
}

/**
 * Deterministic grid maths for the bottle board.
 *
 * The board is a single Compose [Canvas], so everything - bottles, liquid,
 * pour animation, confetti and hit-testing - shares one coordinate space. That
 * removes the classic "animation overlay drifted from the laid-out widget"
 * class of bug.
 *
 * Layout rules:
 *  * bottles are packed into a balanced grid of at most [maxColumns] columns
 *    (4 bottles -> 1x4, 6 -> 2x3, 9 -> 2x5, 13 -> 3x5),
 *  * a short final row is centred,
 *  * every bottle keeps the same [bottleAspect] (width / height) and the same
 *    size, whatever the screen, so the game reads identically on any phone.
 */
class BoardGeometry(
    val bottleCount: Int,
    val width: Float,
    val height: Float,
    val maxColumns: Int = DEFAULT_MAX_COLUMNS,
    val bottleAspect: Float = DEFAULT_ASPECT,
    val fillRatio: Float = DEFAULT_FILL_RATIO,
) {

    val count: Int = max(1, bottleCount)
    val rows: Int
    val columns: Int
    val bottleWidth: Float
    val bottleHeight: Float

    /** One rect per bottle, row-major, index == bottle index. */
    val cells: List<CellRect>

    init {
        val effectiveColumns = max(1, maxColumns)
        rows = max(1, ceil(count / effectiveColumns.toFloat()).toInt())
        columns = max(1, ceil(count / rows.toFloat()).toInt())

        val cellWidth = if (width > 0f) width / columns else 0f
        val cellHeight = if (height > 0f) height / rows else 0f

        var bottleW = cellWidth * fillRatio
        var bottleH = if (bottleW > 0f) bottleW / max(0.05f, bottleAspect) else 0f
        val maxBottleHeight = cellHeight * 0.92f
        if (bottleH > maxBottleHeight && maxBottleHeight > 0f) {
            bottleH = maxBottleHeight
            bottleW = bottleH * max(0.05f, bottleAspect)
        }
        bottleWidth = bottleW
        bottleHeight = bottleH

        val list = ArrayList<CellRect>(count)
        for (index in 0 until count) {
            val row = index / columns
            val column = index % columns
            val inRow = min(columns, count - row * columns)
            val rowOffsetX = (columns - inRow) * cellWidth * 0.5f
            val centerX = rowOffsetX + (column + 0.5f) * cellWidth
            val centerY = (row + 0.5f) * cellHeight
            list += CellRect(
                left = centerX - bottleWidth * 0.5f,
                top = centerY - bottleHeight * 0.5f,
                right = centerX + bottleWidth * 0.5f,
                bottom = centerY + bottleHeight * 0.5f,
            )
        }
        cells = list
    }

    fun cellAt(index: Int): CellRect? = cells.getOrNull(index)

    /**
     * Bottle under a touch point, or -1. The hit area is padded generously -
     * bottles are narrow and this is a game played with thumbs.
     */
    fun indexAt(x: Float, y: Float): Int {
        val slop = max(12f, bottleWidth * 0.18f)
        // Nearest match wins when padded rects overlap.
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (index in cells.indices) {
            val cell = cells[index]
            if (!cell.expandedBy(slop).contains(x, y)) continue
            val dx = x - cell.centerX
            val dy = y - cell.centerY
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    /**
     * Vertical rect occupied by liquid segment [segment] (0 == bottom) inside a
     * bottle whose glass outline is [outer] and whose wall thickness is [wall].
     */
    fun segmentRect(outer: CellRect, wall: Float, segment: Int, capacity: Int): CellRect {
        val inner = outer.inset(wall, wall)
        val step = inner.height / max(1, capacity)
        val top = inner.bottom - (segment + 1) * step
        return CellRect(inner.left, top, inner.right, top + step)
    }

    /** Y coordinate of the liquid surface for [filled] segments. */
    fun surfaceY(outer: CellRect, wall: Float, filled: Int, capacity: Int): Float {
        if (filled <= 0) return outer.bottom - wall
        val inner = outer.inset(wall, wall)
        val step = inner.height / max(1, capacity)
        return inner.bottom - filled * step
    }

    companion object {
        const val DEFAULT_MAX_COLUMNS = 5
        const val DEFAULT_ASPECT = 0.34f
        const val DEFAULT_FILL_RATIO = 0.74f
    }
}
