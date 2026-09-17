package com.colorzen.puzzle.game

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One playable level: its shape plus the pre-generated, solver-verified
 * arrangements. `variants[0]` is the starting layout; the Shuffle button cycles
 * through the others (all three hold exactly the same liquids, just dealt
 * differently, so a shuffle can never make a level unsolvable).
 */
data class LevelDefinition(
    val number: Int,
    val tier: Tier,
    val colorCount: Int,
    val bottleCount: Int,
    val spareBottles: Int,
    val capacity: Int,
    val par: Int,
    val variants: List<List<List<Int>>>,
) {
    fun initialState(variantIndex: Int = 0): List<List<Int>> {
        val index = variantIndex.coerceIn(0, variants.lastIndex)
        return variants[index]
    }

    val variantCount: Int get() = variants.size
}

/**
 * Loads `assets/levels.json` (36 KB, generated and verified offline by
 * `tools/generate_levels.py`).
 *
 * Parsing happens once, off the main thread, behind a mutex; the result is
 * cached for the lifetime of the process.
 */
class LevelRepository(private val context: Context) {

    private val mutex = Mutex()
    private var cache: List<LevelDefinition>? = null

    suspend fun levels(): List<LevelDefinition> = mutex.withLock {
        cache ?: parse().also { cache = it }
    }

    suspend fun level(number: Int): LevelDefinition? =
        levels().firstOrNull { it.number == number }

    private suspend fun parse(): List<LevelDefinition> = withContext(Dispatchers.Default) {
        val text = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        val capacity = root.optInt("capacity", LevelPlan.CAPACITY)
        val array = root.getJSONArray("levels")
        val result = ArrayList<LevelDefinition>(array.length())

        for (index in 0 until array.length()) {
            val entry = array.getJSONObject(index)
            val number = entry.getInt("n")
            val bottles = entry.getInt("bottles")
            val colors = entry.getInt("colors")
            val variantsJson = entry.getJSONArray("variants")
            val variants = ArrayList<List<List<Int>>>(variantsJson.length())

            for (v in 0 until variantsJson.length()) {
                val flat = variantsJson.getJSONArray(v)
                val state = ArrayList<List<Int>>(bottles)
                for (b in 0 until bottles) {
                    val bottle = ArrayList<Int>(capacity)
                    for (s in 0 until capacity) {
                        val colour = flat.optInt(b * capacity + s, 0)
                        if (colour != 0) bottle += colour
                    }
                    state += bottle
                }
                variants += state
            }

            result += LevelDefinition(
                number = number,
                tier = Tier.fromId(entry.optString("t", Tier.BEGINNER.id)),
                colorCount = colors,
                bottleCount = bottles,
                spareBottles = entry.optInt("spares", bottles - colors),
                capacity = capacity,
                par = entry.optInt("par", 12),
                variants = variants,
            )
        }
        result.sortedBy { it.number }
    }

    /**
     * Emergency fallback used only if the asset is missing or unreadable, so a
     * broken build can never show the player an empty board. Levels built this
     * way are solvable but not tuned.
     */
    fun fallbackLevel(number: Int): LevelDefinition {
        val block = LevelPlan.blockFor(number)
        val colors = block.colors
        val bottles = block.bottles
        val state = ArrayList<List<Int>>(bottles)
        var seed = number * 7919
        fun next(): Int {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return seed
        }
        for (colour in 1..colors) {
            state += List(LevelPlan.CAPACITY) { colour }
        }
        // Fisher-Yates over segments, then re-deal into bottles.
        val pool = ArrayList<Int>(colors * LevelPlan.CAPACITY)
        state.forEach { pool += it }
        for (i in pool.indices.reversed()) {
            val j = next() % (i + 1)
            val tmp = pool[i]; pool[i] = pool[j]; pool[j] = tmp
        }
        val dealt = ArrayList<List<Int>>(bottles)
        for (b in 0 until bottles) {
            val start = b * LevelPlan.CAPACITY
            dealt += if (start < pool.size) {
                pool.subList(start, minOf(pool.size, start + LevelPlan.CAPACITY))
            } else {
                emptyList()
            }
        }
        return LevelDefinition(
            number = number,
            tier = block.tier,
            colorCount = colors,
            bottleCount = bottles,
            spareBottles = bottles - colors,
            capacity = LevelPlan.CAPACITY,
            par = colors * 4,
            variants = listOf(dealt),
        )
    }

    companion object {
        const val ASSET_NAME = "levels.json"
    }
}
