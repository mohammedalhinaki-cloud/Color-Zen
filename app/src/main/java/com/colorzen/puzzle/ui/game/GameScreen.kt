package com.colorzen.puzzle.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.colorzen.puzzle.R
import com.colorzen.puzzle.core.AppContainer
import com.colorzen.puzzle.game.LevelPlan
import com.colorzen.puzzle.game.Palettes
import com.colorzen.puzzle.game.Tier
import com.colorzen.puzzle.ui.components.ControlButton
import com.colorzen.puzzle.ui.components.ZenButton
import com.colorzen.puzzle.ui.components.ZenPill
import com.colorzen.puzzle.ui.components.ZenScreen
import com.colorzen.puzzle.ui.components.ZenTopBar
import com.colorzen.puzzle.ui.nav.ZenNavigator
import kotlinx.coroutines.delay

private const val POUR_DURATION_MS = 620
private const val CELEBRATION_DURATION_MS = 1500
private const val NOTICE_VISIBLE_MS = 2600L

/**
 * Gameplay screen: the board, the move counter and the four helpers
 * (Undo, Shuffle, Hint, Restart).
 *
 * The level-complete screen is an animated overlay on top of the solved board,
 * so the celebration flows continuously out of gameplay instead of cutting to a
 * separate destination mid-animation.
 */
@Composable
fun GameScreen(
    levelNumber: Int,
    container: AppContainer,
    navigator: ZenNavigator,
    viewModel: GameViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val palette = remember(state.paletteId) { Palettes.byId(state.paletteId) }
    val style = remember(state.bottleStyleId) { Palettes.styleById(state.bottleStyleId) }

    LaunchedEffect(levelNumber) { viewModel.load(levelNumber) }

    // The pour animation lives in composition because Compose animation needs a
    // frame clock, which viewModelScope does not provide. The ViewModel commits
    // the move only once this reaches 1.0.
    val pourProgress = remember { Animatable(0f) }
    val pourToken = state.pour?.token
    LaunchedEffect(pourToken) {
        if (pourToken == null) return@LaunchedEffect
        pourProgress.snapTo(0f)
        pourProgress.animateTo(targetValue = 1f, animationSpec = tween(POUR_DURATION_MS))
        viewModel.onPourAnimationFinished()
    }

    val celebration = remember { Animatable(0f) }
    LaunchedEffect(state.phase) {
        if (state.phase == GamePhase.COMPLETE) {
            celebration.snapTo(0f)
            celebration.animateTo(targetValue = 1f, animationSpec = tween(CELEBRATION_DURATION_MS))
        } else {
            celebration.snapTo(0f)
        }
    }

    val notice = state.notice
    LaunchedEffect(state.noticeToken) {
        if (notice == GameNotice.NONE) return@LaunchedEffect
        delay(NOTICE_VISIBLE_MS)
        viewModel.dismissNotice()
    }

    val colorNames = rememberColorNames()

    Box(modifier = Modifier.fillMaxSize()) {
        ZenScreen(accent = Color(palette.accent)) {
            ZenTopBar(
                title = stringResource(id = R.string.game_level_title, state.levelNumber),
                subtitle = stringResource(id = tierLabelRes(state.tier)),
                onBack = { navigator.back() },
                actions = {
                    ZenPill(
                        text = stringResource(id = R.string.game_hint_count, state.hintCoins),
                        iconRes = R.drawable.ic_coin,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatPill(
                    label = stringResource(id = R.string.stat_moves),
                    value = state.moves.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatPill(
                    label = stringResource(id = R.string.stat_best),
                    value = state.bestMoves?.toString()
                        ?: stringResource(id = R.string.stat_none),
                    modifier = Modifier.weight(1f),
                )
                StatPill(
                    label = stringResource(id = R.string.stat_sorted),
                    value = "${state.sortedCount}/${state.colorCount}",
                    modifier = Modifier.weight(1f),
                )
            }

            NoticeBanner(notice = notice)

            Box(modifier = Modifier.weight(1f)) {
                BottleBoard(
                    bottles = state.bottles,
                    capacity = state.capacity,
                    palette = palette,
                    style = style,
                    selectedIndex = state.selectedIndex,
                    hint = state.hint,
                    completedBottles = state.completedBottles,
                    pour = state.pour,
                    pourProgress = pourProgress.value,
                    celebration = celebration.value,
                    invalidToken = state.invalidToken,
                    invalidIndex = state.invalidIndex,
                    onBottleTap = viewModel::onBottleTapped,
                    boardDescription = describeBoard(state, colorNames),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                ControlButton(
                    iconRes = R.drawable.ic_undo,
                    label = stringResource(id = R.string.game_undo),
                    onClick = viewModel::onUndo,
                    enabled = state.canUndo && state.phase == GamePhase.PLAYING && !state.isAnimating,
                )
                ControlButton(
                    iconRes = R.drawable.ic_shuffle,
                    label = stringResource(id = R.string.game_shuffle),
                    onClick = viewModel::onShuffle,
                    enabled = state.phase == GamePhase.PLAYING && !state.isAnimating,
                )
                ControlButton(
                    iconRes = R.drawable.ic_hint,
                    label = stringResource(id = R.string.game_hint),
                    onClick = viewModel::onHint,
                    enabled = state.phase == GamePhase.PLAYING && !state.isAnimating,
                    badge = if (state.hintCoins > 0) state.hintCoins.toString() else null,
                )
                ControlButton(
                    iconRes = R.drawable.ic_restart,
                    label = stringResource(id = R.string.game_restart),
                    onClick = viewModel::onRestart,
                    enabled = state.phase == GamePhase.PLAYING && !state.isAnimating,
                )
            }
        }

        if (state.requiresPurchase) {
            LevelLockedDialog(
                packIndex = state.requiredPackIndex,
                onShop = { navigator.toShop() },
                onBack = { navigator.back() },
            )
        }

        if (state.phase == GamePhase.COMPLETE) {
            LevelCompleteOverlay(
                state = state,
                onNext = {
                    if (state.nextLevelAvailable) {
                        navigator.toNextLevel(state.levelNumber + 1)
                    } else {
                        navigator.toShop()
                    }
                },
                onReplay = viewModel::onReplay,
                onLevels = { navigator.toLevels() },
            )
        }
    }
}

/**
 * Compact stat tile. Label above value (rather than one long sentence) so the
 * three counters survive translation into Arabic without truncating.
 */
@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun rememberColorNames(): List<String> = listOf(
    stringResource(id = R.string.color_name_1),
    stringResource(id = R.string.color_name_2),
    stringResource(id = R.string.color_name_3),
    stringResource(id = R.string.color_name_4),
    stringResource(id = R.string.color_name_5),
    stringResource(id = R.string.color_name_6),
    stringResource(id = R.string.color_name_7),
    stringResource(id = R.string.color_name_8),
    stringResource(id = R.string.color_name_9),
    stringResource(id = R.string.color_name_10),
    stringResource(id = R.string.color_name_11),
)

private fun tierLabelRes(tier: Tier): Int = when (tier) {
    Tier.BEGINNER -> R.string.tier_beginner
    Tier.EASY -> R.string.tier_easy
    Tier.MEDIUM -> R.string.tier_medium
    Tier.HARD -> R.string.tier_hard
    Tier.EXPERT -> R.string.tier_expert
    Tier.MASTER -> R.string.tier_master
}

@Composable
private fun NoticeBanner(notice: GameNotice) {
    val message = when (notice) {
        GameNotice.DEAD_END -> stringResource(id = R.string.notice_dead_end)
        GameNotice.NO_HINT_COINS -> stringResource(id = R.string.notice_no_hint_coins)
        GameNotice.INVALID_MOVE -> stringResource(id = R.string.notice_invalid_move)
        GameNotice.HINT_APPROXIMATE -> stringResource(id = R.string.notice_hint_approximate)
        GameNotice.LEVEL_LOCKED -> stringResource(id = R.string.notice_level_locked)
        GameNotice.NONE -> null
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Spoken summary of the board, so the puzzle is playable with TalkBack:
 * bottles and their colours are announced bottom to top.
 */
private fun describeBoard(state: GameUiState, colorNames: List<String>): String {
    if (state.bottles.isEmpty()) return ""
    val bottles = state.bottles.joinToString(separator = ". ") { bottle ->
        val colours = if (bottle.isEmpty()) {
            "empty"
        } else {
            bottle.joinToString(", ") { id -> colorNames.getOrElse(id - 1) { id.toString() } }
        }
        "Bottle: $colours"
    }
    return "Level ${state.levelNumber}. Moves ${state.moves}. " +
        "${state.sortedCount} of ${state.colorCount} bottles sorted. $bottles"
}

/** Purchase gate shown when a paid level is opened without its pack. */
@Composable
private fun LevelLockedDialog(
    packIndex: Int,
    onShop: () -> Unit,
    onBack: () -> Unit,
) {
    val range = LevelPlan.levelsOfPack(packIndex)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = R.string.level_locked_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        id = R.string.level_locked_body,
                        range.first,
                        range.last,
                        packIndex,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZenButton(
                        text = stringResource(id = R.string.level_locked_action_shop),
                        onClick = onShop,
                        modifier = Modifier.weight(1f),
                    )
                    ZenButton(
                        text = stringResource(id = R.string.action_back),
                        onClick = onBack,
                        filled = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
