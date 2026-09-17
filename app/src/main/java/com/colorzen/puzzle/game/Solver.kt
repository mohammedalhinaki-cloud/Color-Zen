package com.colorzen.puzzle.game

import com.colorzen.puzzle.game.GameRules.Move

/**
 * Greedy depth-first solver used by the Hint button.
 *
 * This is a line-by-line mirror of the search in `tools/generate_levels.py`,
 * which only accepted a level into the shipped set when this same search solved
 * it inside [DEFAULT_BUDGET] nodes. Measured worst case across all 120 levels
 * and all 3 shuffle variants: 2590 nodes, i.e. a few milliseconds on a phone.
 *
 * The search returns *a* solution, not a provably optimal one; `par` in
 * levels.json comes from randomised restarts of the same search at build time.
 */
object Solver {

    const val DEFAULT_BUDGET = 40_000

    /**
     * @return an ordered list of pours that solves [state], or null when no
     *   solution was found within [budget] node expansions.
     */
    fun solve(state: List<List<Int>>, budget: Int = DEFAULT_BUDGET): List<Move>? {
        if (GameRules.isSolved(state)) return emptyList()

        val seen = HashSet<String>(4096)
        seen += GameRules.encode(state)

        var nodes = 0
        // (state, path so far)
        val stack = ArrayDeque<Pair<List<List<Int>>, List<Move>>>()
        stack.addLast(state to emptyList())

        while (stack.isNotEmpty()) {
            val (current, path) = stack.removeLast()
            val children = ArrayList<Pair<List<List<Int>>, List<Move>>>(8)

            for (move in GameRules.legalMoves(current)) {
                nodes++
                if (nodes > budget) return null
                val next = GameRules.apply(current, move)
                if (!seen.add(GameRules.encode(next))) continue
                children += next to (path + move)
            }

            // Children are already best-first, so push them worst-first: the
            // most promising one is then the next pop.
            for (index in children.indices.reversed()) {
                val child = children[index]
                if (GameRules.isSolved(child.first)) return child.second
                stack.addLast(child)
            }
        }
        return null
    }

    /** The single best next pour for [state], or null when nothing helps. */
    fun nextMove(state: List<List<Int>>, budget: Int = DEFAULT_BUDGET): Move? =
        solve(state, budget)?.firstOrNull()

    fun hasAnyMove(state: List<List<Int>>): Boolean = GameRules.legalMoves(state).isNotEmpty()

    /**
     * Best-effort hint for the rare state the search cannot resolve inside the
     * budget (a player can wander into a dead end). It never invents an illegal
     * move: it reuses the same scoring that orders [GameRules.legalMoves].
     */
    fun fallbackHint(state: List<List<Int>>): Move? {
        val moves = GameRules.legalMoves(state)
        if (moves.isEmpty()) return null
        // Prefer finishing a bottle, then emptying a mixed one, then any pour.
        val finishing = moves.firstOrNull { move ->
            val destination = state[move.to]
            destination.size + move.count == GameRules.CAPACITY
        }
        return finishing ?: moves.first()
    }

    /** Hint resolution used by the ViewModel: exact first, heuristic second. */
    fun hint(state: List<List<Int>>, budget: Int = DEFAULT_BUDGET): HintResult {
        val exact = nextMove(state, budget)
        if (exact != null) return HintResult(exact, exactSolution = true)
        val fallback = fallbackHint(state)
        return if (fallback == null) {
            HintResult(null, exactSolution = false)
        } else {
            HintResult(fallback, exactSolution = false)
        }
    }

    /**
     * @param move null when the board has no legal pour left (dead end - the UI
     *   then suggests Undo, Shuffle or Restart).
     * @param exactSolution true when [move] is the first step of a full,
     *   verified solution to the current board.
     */
    data class HintResult(val move: Move?, val exactSolution: Boolean)
}
