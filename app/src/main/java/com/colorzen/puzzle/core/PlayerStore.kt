package com.colorzen.puzzle.core

import android.content.Context
import android.content.SharedPreferences
import com.colorzen.puzzle.billing.ProductIds
import com.colorzen.puzzle.game.GameRules
import com.colorzen.puzzle.game.LevelPlan
import com.colorzen.puzzle.game.Palettes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of finishing a level: the fewest moves used and the stars earned. */
data class LevelResult(val bestMoves: Int, val stars: Int)

/** A paused game, so leaving mid-level does not lose progress. */
data class SavedGame(
    val level: Int,
    val variantIndex: Int,
    val moves: Int,
    val bottles: List<List<Int>>,
) {
    fun encode(): String = buildString {
        append(level).append(':').append(variantIndex).append(':').append(moves).append(':')
        bottles.forEachIndexed { index, bottle ->
            if (index > 0) append(';')
            bottle.forEach { append(it).append(',') }
        }
    }

    companion object {
        fun decode(raw: String?): SavedGame? {
            if (raw.isNullOrBlank()) return null
            return try {
                val head = raw.split(':')
                if (head.size < 4) return null
                val level = head[0].toInt()
                val variant = head[1].toInt()
                val moves = head[2].toInt()
                val bottles = head[3].split(';').filter { it.isNotBlank() }.map { bottle ->
                    bottle.split(',').filter { it.isNotBlank() }.map { it.toInt() }
                }
                if (bottles.isEmpty() || level < 1) null else SavedGame(level, variant, moves, bottles)
            } catch (ignored: NumberFormatException) {
                null
            }
        }
    }
}

/**
 * All local persistence, backed by one SharedPreferences file.
 *
 * Everything here stays on the device: no identifiers, no analytics, no server.
 * Values are exposed as [StateFlow]s so Compose screens recompose when progress,
 * coins, entitlements or settings change.
 */
class PlayerStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _soundEffects = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundEffects: StateFlow<Boolean> = _soundEffects.asStateFlow()

    private val _hintCoins = MutableStateFlow(prefs.getInt(KEY_HINTS, AppConfig.STARTING_HINTS))
    val hintCoins: StateFlow<Int> = _hintCoins.asStateFlow()

    private val _ownedProducts = MutableStateFlow(decodeSet(prefs.getString(KEY_OWNED, "")))
    val ownedProducts: StateFlow<Set<String>> = _ownedProducts.asStateFlow()

    /** Purchase tokens already converted into entitlements (idempotency log). */
    private val _grantedTokens = MutableStateFlow(decodeSet(prefs.getString(KEY_TOKENS, "")))

    private val _results = MutableStateFlow(decodeResults(prefs.getString(KEY_RESULTS, "")))
    val results: StateFlow<Map<Int, LevelResult>> = _results.asStateFlow()

    private val _paletteId = MutableStateFlow(
        prefs.getString(KEY_PALETTE, Palettes.default.id) ?: Palettes.default.id,
    )
    val paletteId: StateFlow<String> = _paletteId.asStateFlow()

    private val _bottleStyleId = MutableStateFlow(
        prefs.getString(KEY_BOTTLE_STYLE, "classic") ?: "classic",
    )
    val bottleStyleId: StateFlow<String> = _bottleStyleId.asStateFlow()

    private val _lastLevel = MutableStateFlow(prefs.getInt(KEY_LAST_LEVEL, 1))
    val lastLevel: StateFlow<Int> = _lastLevel.asStateFlow()

    private val _savedGame = MutableStateFlow(SavedGame.decode(prefs.getString(KEY_SAVED_GAME, null)))
    val savedGame: StateFlow<SavedGame?> = _savedGame.asStateFlow()

    private val _hasSeenIntro = MutableStateFlow(prefs.getBoolean(KEY_SEEN_INTRO, false))
    val hasSeenIntro: StateFlow<Boolean> = _hasSeenIntro.asStateFlow()

    // ------------------------------------------------------------- settings

    fun setSoundEffects(enabled: Boolean) {
        if (_soundEffects.value == enabled) return
        _soundEffects.value = enabled
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun markIntroSeen() {
        if (_hasSeenIntro.value) return
        _hasSeenIntro.value = true
        prefs.edit().putBoolean(KEY_SEEN_INTRO, true).apply()
    }

    fun setPalette(id: String) {
        _paletteId.value = id
        prefs.edit().putString(KEY_PALETTE, id).apply()
    }

    fun setBottleStyle(id: String) {
        _bottleStyleId.value = id
        prefs.edit().putString(KEY_BOTTLE_STYLE, id).apply()
    }

    fun setLastLevel(level: Int) {
        val clamped = level.coerceIn(1, LevelPlan.TOTAL_LEVELS)
        _lastLevel.value = clamped
        prefs.edit().putInt(KEY_LAST_LEVEL, clamped).apply()
    }

    // --------------------------------------------------------------- hints

    fun addHintCoins(amount: Int) {
        if (amount == 0) return
        val next = (_hintCoins.value + amount).coerceAtLeast(0)
        _hintCoins.value = next
        prefs.edit().putInt(KEY_HINTS, next).apply()
    }

    /** @return true when the coin was available and spent. */
    fun spendHintCoin(): Boolean {
        if (_hintCoins.value < AppConfig.HINT_COST_COINS) return false
        addHintCoins(-AppConfig.HINT_COST_COINS)
        return true
    }

    // ------------------------------------------------------------ purchases

    /** @return true when this product was not already owned. */
    fun markOwned(productId: String): Boolean {
        if (productId.isBlank()) return false
        if (productId in _ownedProducts.value) return false
        val next = _ownedProducts.value + productId
        _ownedProducts.value = next
        prefs.edit().putString(KEY_OWNED, encodeSet(next)).apply()
        return true
    }

    /** @return true the first time this purchase token is seen. */
    fun markTokenGranted(token: String): Boolean {
        if (token.isBlank()) return false
        if (token in _grantedTokens.value) return false
        val next = (_grantedTokens.value + token).takeLast(MAX_TOKENS)
        _grantedTokens.value = next
        prefs.edit().putString(KEY_TOKENS, encodeSet(next)).apply()
        return true
    }

    fun owns(productId: String): Boolean = productId in _ownedProducts.value

    /** The one-time "Full Game" unlock supersedes every individual pack. */
    val ownsFullGame: Boolean get() = Entitlements.ownsFullGame(_ownedProducts.value)

    /** Extra colour palettes + bottle designs. */
    val ownsThemes: Boolean get() = Entitlements.ownsThemes(_ownedProducts.value)

    fun ownsPack(packIndex: Int): Boolean = Entitlements.ownsPack(_ownedProducts.value, packIndex)

    // ------------------------------------------------------------- progress

    fun isCompleted(level: Int): Boolean = level in _results.value

    fun resultFor(level: Int): LevelResult? = _results.value[level]

    fun completedCount(): Int = _results.value.size

    fun starsFor(level: Int): Int = _results.value[level]?.stars ?: 0

    fun totalStars(): Int = _results.value.values.sumOf { it.stars }

    /**
     * Records a finished level, keeping the best (fewest) moves and the best
     * star count ever earned for it.
     * @return true when this run improved on the stored record.
     */
    fun recordCompletion(level: Int, moves: Int, stars: Int): Boolean {
        val existing = _results.value[level]
        val bestMoves = if (existing == null) moves else minOf(existing.bestMoves, moves)
        val bestStars = if (existing == null) stars else maxOf(existing.stars, stars)
        val improved = existing == null || bestMoves < existing.bestMoves || bestStars > existing.stars
        val next = _results.value.toMutableMap()
        next[level] = LevelResult(bestMoves, bestStars)
        _results.value = next
        prefs.edit().putString(KEY_RESULTS, encodeResults(next)).apply()
        setLastLevel((level + 1).coerceAtMost(LevelPlan.TOTAL_LEVELS))
        return improved
    }

    /**
     * A level is playable when its pack is owned and the previous one is done.
     *
     * Convenience for non-UI callers. Compose screens should read
     * [ownedProducts] / [results] through `collectAsState()` and call
     * [Entitlements] directly, otherwise a purchase will not refresh the grid.
     */
    fun isLevelUnlocked(level: Int): Boolean =
        Entitlements.isLevelUnlocked(_ownedProducts.value, _results.value.keys, level)

    /** Why a level is not playable yet - drives the level-select UI. */
    fun levelLockReason(level: Int): LevelLock =
        Entitlements.lockReason(_ownedProducts.value, _results.value.keys, level)

    /**
     * First level the player has not finished yet - what the menu's Play button
     * and a "finish the previous level first" tap both resolve to. If its pack
     * is not owned, the game screen shows the purchase prompt.
     */
    fun nextPlayableLevel(): Int = Entitlements.nextPlayableLevel(_results.value.keys)

    // ----------------------------------------------------------- saved game

    fun saveGame(game: SavedGame?) {
        _savedGame.value = game
        prefs.edit()
            .apply { if (game == null) remove(KEY_SAVED_GAME) else putString(KEY_SAVED_GAME, game.encode()) }
            .apply()
    }

    fun clearSavedGame() = saveGame(null)

    /** Force any pending writes to disk (called after a purchase batch). */
    fun persist() {
        prefs.edit().commit()
    }

    // -------------------------------------------------------------- helpers

    private fun decodeSet(raw: String?): Set<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private fun encodeSet(values: Set<String>): String = values.joinToString(SEPARATOR)

    private fun decodeResults(raw: String?): Map<Int, LevelResult> {
        if (raw.isNullOrBlank()) return emptyMap()
        val map = LinkedHashMap<Int, LevelResult>()
        raw.split(SEPARATOR).forEach { entry ->
            val parts = entry.split(',')
            if (parts.size == 3) {
                val level = parts[0].toIntOrNull() ?: return@forEach
                val moves = parts[1].toIntOrNull() ?: return@forEach
                val stars = parts[2].toIntOrNull() ?: return@forEach
                map[level] = LevelResult(moves, stars)
            }
        }
        return map
    }

    private fun encodeResults(results: Map<Int, LevelResult>): String =
        results.entries.sortedBy { it.key }
            .joinToString(SEPARATOR) { "${it.key},${it.value.bestMoves},${it.value.stars}" }

    companion object {
        private const val FILE_NAME = "colorzen_player"
        private const val SEPARATOR = "|"
        private const val MAX_TOKENS = 200

        private const val KEY_SOUND = "sound_effects"
        private const val KEY_HINTS = "hint_coins"
        private const val KEY_OWNED = "owned_products"
        private const val KEY_TOKENS = "granted_tokens"
        private const val KEY_RESULTS = "level_results"
        private const val KEY_PALETTE = "palette_id"
        private const val KEY_BOTTLE_STYLE = "bottle_style_id"
        private const val KEY_LAST_LEVEL = "last_level"
        private const val KEY_SAVED_GAME = "saved_game"
        private const val KEY_SEEN_INTRO = "seen_intro"
    }
}

/** Why a level tile in the grid is not tappable. */
sealed interface LevelLock {
    data object Unlocked : LevelLock
    /** An earlier level in the same pack has to be finished first. */
    data object PreviousIncomplete : LevelLock
    /** The pack that contains this level has not been purchased. */
    data class PackRequired(val packIndex: Int) : LevelLock
}

/** Kept next to the rules so tests can assert the invariant. */
internal fun validateState(state: List<List<Int>>): Boolean {
    val counts = GameRules.colourCounts(state)
    return counts.values.all { it == GameRules.CAPACITY }
}
