package com.colorzen.puzzle.game

/**
 * Pure water-sort rules.
 *
 * A bottle is an immutable [List] of colour ids ordered bottom -> top. A list
 * shorter than [CAPACITY] has free space at the top; an empty list is an empty
 * bottle. Colour ids are 1-based (0 is never stored).
 *
 * This file has no Android or Compose dependencies on purpose: the whole rule
 * set is covered by JVM unit tests and mirrored 1:1 by
 * `tools/generate_levels.py`, which proved every shipped level solvable.
 */
object GameRules {

    /** Liquid segments a bottle can hold. */
    const val CAPACITY = 4

    /** A pour: move [count] top segments from bottle [from] into bottle [to]. */
    data class Move(val from: Int, val to: Int, val count: Int)

    /** Colour and size of the topmost contiguous run of a bottle. */
    fun topRun(bottle: List<Int>): Pair<Int, Int> {
        if (bottle.isEmpty()) return 0 to 0
        val colour = bottle.last()
        var count = 0
        for (index in bottle.indices.reversed()) {
            if (bottle[index] != colour) break
            count++
        }
        return colour to count
    }

    fun freeSpace(bottle: List<Int>): Int = CAPACITY - bottle.size

    /**
     * How many segments can legally be poured from [from] into [to], or 0 when
     * the pour is not allowed.
     *
     * Rules: the source must hold liquid, the destination must have room, and
     * the destination must be empty or show the same colour on top. When the
     * destination cannot take the whole run, as many segments as fit are poured
     * (the forgiving rule used by water-sort games).
     */
    fun pourCount(state: List<List<Int>>, from: Int, to: Int): Int {
        if (from == to) return 0
        if (from !in state.indices || to !in state.indices) return 0
        val source = state[from]
        val destination = state[to]
        if (source.isEmpty()) return 0
        val free = freeSpace(destination)
        if (free <= 0) return 0
        val (colour, run) = topRun(source)
        if (destination.isNotEmpty() && destination.last() != colour) return 0
        return minOf(run, free)
    }

    fun canPour(state: List<List<Int>>, from: Int, to: Int): Boolean =
        pourCount(state, from, to) > 0

    /** Returns a new state with [move] applied. Invalid moves are a no-op. */
    fun apply(state: List<List<Int>>, move: Move): List<List<Int>> {
        val count = pourCount(state, move.from, move.to)
            .coerceAtMost(move.count)
        if (count <= 0) return state
        val next = state.toMutableList()
        val source = state[move.from]
        val destination = state[move.to]
        next[move.from] = source.subList(0, source.size - count)
        next[move.to] = destination + source.subList(source.size - count, source.size)
        return next
    }

    /** A bottle is finished when it is empty or holds [CAPACITY] of one colour. */
    fun isBottleSorted(bottle: List<Int>): Boolean =
        bottle.isEmpty() || (bottle.size == CAPACITY && bottle.all { it == bottle.first() })

    fun isSolved(state: List<List<Int>>): Boolean = state.all { isBottleSorted(it) }

    /** Indices of bottles that are full and single-coloured. */
    fun completedBottles(state: List<List<Int>>): Set<Int> =
        state.withIndex()
            .filter { (_, bottle) -> bottle.size == CAPACITY && bottle.all { it == bottle.first() } }
            .map { it.index }
            .toSet()

    /**
     * Every legal pour, most promising first.
     *
     * Ordering mirrors the Python generator so that hint quality and the
     * pre-computed `par` values stay in sync:
     *   1. pours that finish a bottle,
     *   2. pours that empty the source or land on a matching stack,
     *   3. bigger pours.
     */
    fun legalMoves(state: List<List<Int>>): List<Move> {
        val moves = ArrayList<ScoredMove>(state.size * state.size)
        for (i in state.indices) {
            val source = state[i]
            if (source.isEmpty()) continue
            val (colour, run) = topRun(source)
            val sourceIsPure = source.all { it == colour }
            for (j in state.indices) {
                if (i == j) continue
                val destination = state[j]
                val free = freeSpace(destination)
                if (free <= 0) continue
                if (destination.isNotEmpty()) {
                    if (destination.last() != colour) continue
                } else if (sourceIsPure) {
                    // Relocating an already clean bottle into an empty one is a
                    // no-op that only wastes a move.
                    continue
                }
                val count = minOf(run, free)
                val completes =
                    if (destination.size + count == CAPACITY &&
                        (destination.isEmpty() || destination.first() == colour)
                    ) 1 else 0
                val empties = if (count == source.size) 1 else 0
                val ontoMatch = if (destination.isNotEmpty()) 1 else 0
                moves += ScoredMove(Move(i, j, count), completes, empties + ontoMatch, count)
            }
        }
        moves.sortWith(SCORE_ORDER)
        return moves.map { it.move }
    }

    private class ScoredMove(
        val move: Move,
        val completes: Int,
        val freesSource: Int,
        val size: Int,
    )

    private val SCORE_ORDER = Comparator<ScoredMove> { a, b ->
        var result = b.completes.compareTo(a.completes)
        if (result == 0) result = b.freesSource.compareTo(a.freesSource)
        if (result == 0) result = b.size.compareTo(a.size)
        result
    }

    /**
     * Bottles are interchangeable, so sorting them collapses states that differ
     * only by permutation. This prunes the search massively and is safe because
     * both the move rules and the win condition are symmetric.
     */
    fun canonical(state: List<List<Int>>): List<List<Int>> = state.sortedWith(BOTTLE_ORDER)

    private val BOTTLE_ORDER = Comparator<List<Int>> { a, b ->
        val limit = minOf(a.size, b.size)
        for (index in 0 until limit) {
            val compare = a[index].compareTo(b[index])
            if (compare != 0) return@Comparator compare
        }
        a.size.compareTo(b.size)
    }

    /** Stable string key for the visited set of [canonical]. */
    fun encode(state: List<List<Int>>): String = buildString(state.size * (CAPACITY * 2 + 1)) {
        for (bottle in canonical(state)) {
            for (colour in bottle) {
                append(if (colour < 10) '0' + colour else 'a' + (colour - 10))
            }
            append('|')
        }
    }

    /** Colour histogram - used to validate that a shuffle keeps the same liquids. */
    fun colourCounts(state: List<List<Int>>): Map<Int, Int> {
        val counts = LinkedHashMap<Int, Int>()
        for (bottle in state) {
            for (colour in bottle) counts[colour] = (counts[colour] ?: 0) + 1
        }
        return counts
    }
}
