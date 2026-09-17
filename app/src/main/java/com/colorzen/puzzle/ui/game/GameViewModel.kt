package com.colorzen.puzzle.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.colorzen.puzzle.core.AppConfig
import com.colorzen.puzzle.core.SavedGame
import com.colorzen.puzzle.core.Sfx
import com.colorzen.puzzle.core.appContainer
import com.colorzen.puzzle.game.BottleStyle
import com.colorzen.puzzle.game.GameRules
import com.colorzen.puzzle.game.LevelDefinition
import com.colorzen.puzzle.game.LevelPlan
import com.colorzen.puzzle.game.Palettes
import com.colorzen.puzzle.game.Solver
import com.colorzen.puzzle.game.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GamePhase { LOADING, PLAYING, COMPLETE }

/** Transient messages shown as a banner; the token lets the UI re-trigger them. */
enum class GameNotice {
    NONE,
    DEAD_END,
    NO_HINT_COINS,
    INVALID_MOVE,
    HINT_APPROXIMATE,
    LEVEL_LOCKED,
}

data class GameUiState(
    val phase: GamePhase = GamePhase.LOADING,
    val levelNumber: Int = 1,
    val tier: Tier = Tier.BEGINNER,
    val bottles: List<List<Int>> = emptyList(),
    val capacity: Int = GameRules.CAPACITY,
    val colorCount: Int = 0,
    val bottleCount: Int = 0,
    val moves: Int = 0,
    val par: Int = 0,
    val selectedIndex: Int = -1,
    val pour: PendingPour? = null,
    val hint: GameRules.Move? = null,
    val hintCoins: Int = 0,
    val stars: Int = 0,
    val bestMoves: Int? = null,
    val isRecord: Boolean = false,
    val canUndo: Boolean = false,
    val variantIndex: Int = 0,
    val variantCount: Int = 1,
    val completedBottles: Set<Int> = emptySet(),
    /** Bottle that just became full - drives the bubble sound + pop animation. */
    val justFilledBottle: Int = -1,
    val justFilledToken: Long = 0L,
    val paletteId: String = Palettes.default.id,
    val bottleStyleId: String = BottleStyle.CLASSIC.id,
    val notice: GameNotice = GameNotice.NONE,
    val noticeToken: Long = 0L,
    val invalidToken: Long = 0L,
    val invalidIndex: Int = -1,
    val nextLevelAvailable: Boolean = false,
    val isCampaignFinished: Boolean = false,
    val requiresPurchase: Boolean = false,
    val requiredPackIndex: Int = 0,
    val totalLevels: Int = LevelPlan.TOTAL_LEVELS,
) {
    val sortedCount: Int get() = completedBottles.size
    val isAnimating: Boolean get() = pour != null
}

/**
 * Gameplay state machine.
 *
 * The ViewModel owns the rules and the committed state; the pour *animation* is
 * owned by the composition (a Compose animation needs a frame clock, which a
 * viewModelScope does not have). The handshake is:
 *
 *   tap -> ViewModel publishes [PendingPour] (model unchanged)
 *   UI animates 0..1
 *   UI calls [onPourAnimationFinished] -> ViewModel commits the move
 *
 * That keeps animation and rules from ever disagreeing, and makes undo trivial:
 * the model only ever changes at commit time.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val store = container.playerStore
    private val sound = container.soundManager
    private val repository = container.levelRepository

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var definition: LevelDefinition? = null
    private var requestedLevel = -1
    private var hintJob: Job? = null
    private var tokenCounter = 0L

    private class Snapshot(
        val bottles: List<List<Int>>,
        val moves: Int,
        val variantIndex: Int,
    )

    private val undoStack = ArrayDeque<Snapshot>()

    init {
        viewModelScope.launch {
            combine(store.hintCoins, store.paletteId, store.bottleStyleId) { coins, palette, style ->
                Triple(coins, palette, style)
            }.collect { (coins, palette, style) ->
                _uiState.update {
                    it.copy(hintCoins = coins, paletteId = palette, bottleStyleId = style)
                }
            }
        }
    }

    private fun nextToken(): Long = ++tokenCounter

    /** Loads (or resumes) a level. Safe to call repeatedly. */
    fun load(levelNumber: Int) {
        if (requestedLevel == levelNumber && _uiState.value.phase != GamePhase.LOADING) return
        requestedLevel = levelNumber
        undoStack.clear()
        hintJob?.cancel()

        viewModelScope.launch {
            val loaded = repository.level(levelNumber) ?: repository.fallbackLevel(levelNumber)
            definition = loaded

            val saved = store.savedGame.value?.takeIf { it.level == levelNumber }
            val variantIndex = saved?.variantIndex?.coerceIn(0, loaded.variantCount - 1) ?: 0
            val bottles = saved?.bottles?.takeIf { it.size == loaded.bottleCount }
                ?: loaded.initialState(variantIndex)
            val moves = saved?.moves ?: 0

            val packIndex = LevelPlan.packIndexFor(levelNumber)
            val unlocked = store.isLevelUnlocked(levelNumber)

            _uiState.value = GameUiState(
                phase = GamePhase.PLAYING,
                levelNumber = levelNumber,
                tier = loaded.tier,
                bottles = bottles,
                capacity = loaded.capacity,
                colorCount = loaded.colorCount,
                bottleCount = loaded.bottleCount,
                moves = moves,
                par = loaded.par,
                variantIndex = variantIndex,
                variantCount = loaded.variantCount,
                completedBottles = GameRules.completedBottles(bottles),
                hintCoins = store.hintCoins.value,
                paletteId = store.paletteId.value,
                bottleStyleId = store.bottleStyleId.value,
                bestMoves = store.resultFor(levelNumber)?.bestMoves,
                requiresPurchase = !unlocked,
                requiredPackIndex = packIndex,
                nextLevelAvailable = levelNumber < LevelPlan.TOTAL_LEVELS &&
                    store.ownsPack(LevelPlan.packIndexFor(levelNumber + 1)),
                isCampaignFinished = levelNumber >= LevelPlan.TOTAL_LEVELS,
            )
            store.setLastLevel(levelNumber)
        }
    }

    // ------------------------------------------------------------ interaction

    fun onBottleTapped(index: Int) {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING || state.isAnimating || state.requiresPurchase) return
        if (index !in state.bottles.indices) return

        val selected = state.selectedIndex

        if (selected < 0) {
            if (state.bottles[index].isEmpty()) {
                sound.play(Sfx.CLICK, 0.6f)
                flash(GameNotice.INVALID_MOVE, index)
                return
            }
            sound.play(Sfx.SELECT)
            _uiState.update { it.copy(selectedIndex = index, hint = null) }
            return
        }

        if (selected == index) {
            sound.play(Sfx.CLICK, 0.8f)
            _uiState.update { it.copy(selectedIndex = -1) }
            return
        }

        val count = GameRules.pourCount(state.bottles, selected, index)
        if (count > 0) {
            sound.play(Sfx.POUR)
            _uiState.update {
                it.copy(
                    pour = PendingPour(nextToken(), selected, index, count),
                    selectedIndex = -1,
                    hint = null,
                )
            }
        } else {
            sound.play(Sfx.CLICK, 0.55f)
            flash(GameNotice.INVALID_MOVE, index)
            // Tapping another bottle with liquid moves the selection to it,
            // which is what players expect after a rejected pour.
            _uiState.update {
                it.copy(
                    selectedIndex = if (it.bottles[index].isNotEmpty()) index else -1,
                    hint = null,
                )
            }
        }
    }

    /** Called by the UI when the pour animation reaches 1.0. */
    fun onPourAnimationFinished() {
        val state = _uiState.value
        val pour = state.pour ?: return
        commitMove(GameRules.Move(pour.from, pour.to, pour.count))
    }

    private fun flash(notice: GameNotice, bottleIndex: Int) {
        val token = nextToken()
        _uiState.update {
            it.copy(
                notice = notice,
                noticeToken = token,
                invalidIndex = bottleIndex,
                invalidToken = token,
            )
        }
    }

    private fun commitMove(move: GameRules.Move) {
        val state = _uiState.value
        val before = state.bottles
        val after = GameRules.apply(before, move)
        if (after == before) {
            _uiState.update { it.copy(pour = null) }
            return
        }

        undoStack.addLast(Snapshot(before, state.moves, state.variantIndex))

        val completedBefore = GameRules.completedBottles(before)
        val completedAfter = GameRules.completedBottles(after)
        val newlyFilled = (completedAfter - completedBefore).minOrNull() ?: -1
        if (newlyFilled >= 0) sound.play(Sfx.BUBBLE)

        val moves = state.moves + 1

        if (GameRules.isSolved(after)) {
            val stars = LevelPlan.starsFor(moves, state.par)
            val isRecord = store.recordCompletion(state.levelNumber, moves, stars)
            store.clearSavedGame()
            sound.play(Sfx.COMPLETE)
            val nextLevel = state.levelNumber + 1
            _uiState.value = state.copy(
                phase = GamePhase.COMPLETE,
                bottles = after,
                moves = moves,
                pour = null,
                selectedIndex = -1,
                hint = null,
                completedBottles = completedAfter,
                justFilledBottle = newlyFilled,
                justFilledToken = nextToken(),
                stars = stars,
                isRecord = isRecord,
                bestMoves = store.resultFor(state.levelNumber)?.bestMoves,
                canUndo = false,
                nextLevelAvailable = nextLevel <= LevelPlan.TOTAL_LEVELS &&
                    store.ownsPack(LevelPlan.packIndexFor(nextLevel)),
                isCampaignFinished = state.levelNumber >= LevelPlan.TOTAL_LEVELS,
                notice = GameNotice.NONE,
            )
            return
        }

        val stillPlayable = Solver.hasAnyMove(after)
        _uiState.value = state.copy(
            bottles = after,
            moves = moves,
            pour = null,
            selectedIndex = -1,
            hint = null,
            completedBottles = completedAfter,
            justFilledBottle = newlyFilled,
            justFilledToken = if (newlyFilled >= 0) nextToken() else state.justFilledToken,
            canUndo = undoStack.isNotEmpty(),
            notice = if (stillPlayable) GameNotice.NONE else GameNotice.DEAD_END,
            noticeToken = if (stillPlayable) state.noticeToken else nextToken(),
        )
        persist(_uiState.value)
    }

    fun onUndo() {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING || state.isAnimating) return
        val snapshot = undoStack.removeLastOrNull() ?: return
        sound.play(Sfx.CLICK)
        val restored = state.copy(
            bottles = snapshot.bottles,
            moves = snapshot.moves,
            variantIndex = snapshot.variantIndex,
            selectedIndex = -1,
            hint = null,
            pour = null,
            completedBottles = GameRules.completedBottles(snapshot.bottles),
            canUndo = undoStack.isNotEmpty(),
            justFilledBottle = -1,
            notice = GameNotice.NONE,
        )
        _uiState.value = restored
        persist(restored)
    }

    /**
     * Re-deals the same liquids into a different verified arrangement.
     *
     * Shuffle never invents a random layout: it cycles the level's
     * solver-verified variants, so a shuffled board is always still solvable.
     */
    fun onShuffle() {
        val state = _uiState.value
        val level = definition ?: return
        if (state.phase != GamePhase.PLAYING || state.isAnimating) return

        val nextVariant = if (level.variantCount > 1) {
            (state.variantIndex + 1) % level.variantCount
        } else {
            0
        }
        val bottles = level.initialState(nextVariant)
        undoStack.addLast(Snapshot(state.bottles, state.moves, state.variantIndex))
        sound.play(Sfx.SELECT)
        val shuffled = state.copy(
            bottles = bottles,
            variantIndex = nextVariant,
            moves = state.moves + 1,
            selectedIndex = -1,
            hint = null,
            completedBottles = GameRules.completedBottles(bottles),
            canUndo = undoStack.isNotEmpty(),
            justFilledBottle = -1,
            notice = GameNotice.NONE,
        )
        _uiState.value = shuffled
        persist(shuffled)
    }

    /** Back to the layout this attempt started from, with the move counter reset. */
    fun onRestart() {
        val state = _uiState.value
        val level = definition ?: return
        if (state.isAnimating) return
        val bottles = level.initialState(state.variantIndex)
        undoStack.clear()
        sound.play(Sfx.CLICK)
        val restarted = GameUiState(
            phase = GamePhase.PLAYING,
            levelNumber = state.levelNumber,
            tier = state.tier,
            bottles = bottles,
            capacity = state.capacity,
            colorCount = state.colorCount,
            bottleCount = state.bottleCount,
            moves = 0,
            par = state.par,
            variantIndex = state.variantIndex,
            variantCount = state.variantCount,
            completedBottles = GameRules.completedBottles(bottles),
            hintCoins = store.hintCoins.value,
            paletteId = store.paletteId.value,
            bottleStyleId = store.bottleStyleId.value,
            bestMoves = store.resultFor(state.levelNumber)?.bestMoves,
            nextLevelAvailable = state.nextLevelAvailable,
            isCampaignFinished = state.isCampaignFinished,
            requiresPurchase = state.requiresPurchase,
            requiredPackIndex = state.requiredPackIndex,
            totalLevels = state.totalLevels,
        )
        _uiState.value = restarted
        persist(restarted)
    }

    /**
     * Spends one hint coin and reveals the best next pour.
     *
     * The search runs on [Dispatchers.Default]; levels were only shipped if this
     * same search solves them in a few thousand nodes, so it is effectively
     * instant. The coin is charged only when a real move was found.
     */
    fun onHint() {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING || state.isAnimating) return

        if (state.hint != null) {
            sound.play(Sfx.HINT)
            return
        }
        if (state.hintCoins < AppConfig.HINT_COST_COINS) {
            sound.play(Sfx.CLICK, 0.6f)
            val token = nextToken()
            _uiState.update { it.copy(notice = GameNotice.NO_HINT_COINS, noticeToken = token) }
            return
        }

        val board = state.bottles
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { Solver.hint(board) }
            val current = _uiState.value
            if (current.phase != GamePhase.PLAYING || current.pour != null) return@launch

            val move = result.move
            if (move == null) {
                sound.play(Sfx.CLICK, 0.6f)
                val token = nextToken()
                _uiState.update { it.copy(notice = GameNotice.DEAD_END, noticeToken = token) }
                return@launch
            }

            if (!store.spendHintCoin()) {
                val token = nextToken()
                _uiState.update { it.copy(notice = GameNotice.NO_HINT_COINS, noticeToken = token) }
                return@launch
            }

            sound.play(Sfx.HINT)
            _uiState.update {
                it.copy(
                    hint = move,
                    hintCoins = store.hintCoins.value,
                    selectedIndex = -1,
                    notice = if (result.exactSolution) {
                        GameNotice.NONE
                    } else {
                        GameNotice.HINT_APPROXIMATE
                    },
                    noticeToken = if (result.exactSolution) it.noticeToken else nextToken(),
                )
            }
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = GameNotice.NONE) }
    }

    /** Replay the same level from scratch (used by the completion screen). */
    fun onReplay() {
        undoStack.clear()
        val state = _uiState.value
        val level = definition ?: return
        val bottles = level.initialState(state.variantIndex)
        sound.play(Sfx.CLICK)
        _uiState.value = state.copy(
            phase = GamePhase.PLAYING,
            bottles = bottles,
            moves = 0,
            selectedIndex = -1,
            hint = null,
            pour = null,
            stars = 0,
            isRecord = false,
            completedBottles = GameRules.completedBottles(bottles),
            canUndo = false,
            justFilledBottle = -1,
            notice = GameNotice.NONE,
        )
        persist(_uiState.value)
    }

    private fun persist(state: GameUiState) {
        if (state.phase != GamePhase.PLAYING || state.requiresPurchase) return
        store.saveGame(
            SavedGame(
                level = state.levelNumber,
                variantIndex = state.variantIndex,
                moves = state.moves,
                bottles = state.bottles,
            ),
        )
    }

    override fun onCleared() {
        hintJob?.cancel()
        super.onCleared()
    }
}
