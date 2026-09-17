package com.colorzen.puzzle.game

/** Difficulty tier. Labels are resolved to strings in the UI layer. */
enum class Tier(val id: String) {
    BEGINNER("beginner"),
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard"),
    EXPERT("expert"),
    MASTER("master");

    companion object {
        fun fromId(id: String): Tier = entries.firstOrNull { it.id == id } ?: BEGINNER
    }
}

/**
 * Static description of the 120-level campaign.
 *
 * Mirrors `TIERS` in `tools/generate_levels.py`. Keeping the shape table here
 * means the level-select grid, the pack gating and the difficulty headers never
 * need the level asset file, so that screen renders instantly.
 *
 * Free tiers 1-30 use exactly the shapes from the product spec (4 bottles / 3
 * colours, 5 / 4, 6 / 5). Paid tiers use "7+ bottles, 6+ colours" and add a
 * second spare bottle, which is what makes deep layouts reliably solvable.
 */
object LevelPlan {

    const val TOTAL_LEVELS = 120
    const val FREE_LEVELS = 30
    const val LEVELS_PER_PACK = 30
    const val CAPACITY = GameRules.CAPACITY

    /** Number of purchasable level packs: (120 - 30) / 30. */
    const val PACK_COUNT = 3

    data class Block(
        val tier: Tier,
        val fromLevel: Int,
        val toLevel: Int,
        val bottles: Int,
        val colors: Int,
    ) {
        val spareBottles: Int get() = bottles - colors
    }

    val blocks: List<Block> = listOf(
        Block(Tier.BEGINNER, 1, 10, bottles = 4, colors = 3),
        Block(Tier.EASY, 11, 20, bottles = 5, colors = 4),
        Block(Tier.MEDIUM, 21, 30, bottles = 6, colors = 5),
        Block(Tier.HARD, 31, 45, bottles = 8, colors = 6),
        Block(Tier.HARD, 46, 60, bottles = 9, colors = 7),
        Block(Tier.EXPERT, 61, 75, bottles = 10, colors = 8),
        Block(Tier.EXPERT, 76, 90, bottles = 11, colors = 9),
        Block(Tier.MASTER, 91, 105, bottles = 12, colors = 10),
        Block(Tier.MASTER, 106, 120, bottles = 13, colors = 11),
    )

    /** Sections shown in the level-select screen: one per difficulty tier. */
    data class Section(val tier: Tier, val fromLevel: Int, val toLevel: Int)

    val sections: List<Section> = Tier.entries.mapNotNull { tier ->
        val levels = blocks.filter { it.tier == tier }
        if (levels.isEmpty()) null else Section(
            tier = tier,
            fromLevel = levels.minOf { it.fromLevel },
            toLevel = levels.maxOf { it.toLevel },
        )
    }

    fun blockFor(level: Int): Block {
        val clamped = level.coerceIn(1, TOTAL_LEVELS)
        return blocks.firstOrNull { clamped in it.fromLevel..it.toLevel } ?: blocks.last()
    }

    fun tierFor(level: Int): Tier = blockFor(level).tier

    fun bottlesFor(level: Int): Int = blockFor(level).bottles

    fun colorsFor(level: Int): Int = blockFor(level).colors

    /**
     * Pack index that gates [level]: 0 == free starter set, 1..3 == paid pack.
     * Pack 1 covers 31-60, pack 2 covers 61-90, pack 3 covers 91-120.
     */
    fun packIndexFor(level: Int): Int {
        if (level <= FREE_LEVELS) return 0
        val beyond = level - FREE_LEVELS
        return ((beyond - 1) / LEVELS_PER_PACK + 1).coerceIn(1, PACK_COUNT)
    }

    fun levelsOfPack(packIndex: Int): IntRange {
        val first = FREE_LEVELS + (packIndex - 1) * LEVELS_PER_PACK + 1
        return first..minOf(TOTAL_LEVELS, first + LEVELS_PER_PACK - 1)
    }

    /**
     * Star thresholds. `par` is a near-optimal solution length computed at build
     * time, so the bands are generous: perfect play is 3 stars, tidy play is 2,
     * finishing at all is always at least 1.
     */
    fun starsFor(moves: Int, par: Int): Int {
        if (par <= 0) return 1
        val ratio = moves.toFloat() / par.toFloat()
        return when {
            ratio <= 1.25f -> 3
            ratio <= 1.75f -> 2
            else -> 1
        }
    }
}
