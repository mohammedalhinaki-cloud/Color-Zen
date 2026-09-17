package com.colorzen.puzzle.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.colorzen.puzzle.R
import com.colorzen.puzzle.game.LevelPlan
import com.colorzen.puzzle.ui.components.ZenButton
import com.colorzen.puzzle.ui.theme.ZenStar

/**
 * Level Complete screen.
 *
 * Shown as an overlay on the solved board so the confetti started by
 * [BottleBoard] keeps falling behind the card. Stars pop in one at a time; the
 * summary is moves vs. the near-optimal `par` computed at build time.
 */
@Composable
fun LevelCompleteOverlay(
    state: GameUiState,
    onNext: () -> Unit,
    onReplay: () -> Unit,
    onLevels: () -> Unit,
) {
    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardVisible = true }

    val starScales = listOf(
        remember { Animatable(0f) },
        remember { Animatable(0f) },
        remember { Animatable(0f) },
    )
    LaunchedEffect(state.stars, state.levelNumber) {
        starScales.forEach { it.snapTo(0f) }
        for (index in 0 until state.stars.coerceAtMost(starScales.size)) {
            delay(220L)
            starScales[index].animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    val finishedCampaign = state.isCampaignFinished && state.levelNumber >= LevelPlan.TOTAL_LEVELS

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f)),
        )

        AnimatedVisibility(
            visible = cardVisible,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300)),
            exit = fadeOut(tween(120)),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.game_level_title,
                            state.levelNumber,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            id = if (finishedCampaign) {
                                R.string.campaign_finished_title
                            } else {
                                R.string.level_complete_title
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        starScales.forEachIndexed { index, scaleAnim ->
                            val earned = index < state.stars
                            Icon(
                                painter = painterResource(
                                    id = if (earned) R.drawable.ic_star else R.drawable.ic_star_outline,
                                ),
                                contentDescription = stringResource(
                                    id = if (earned) R.string.star_earned else R.string.star_empty,
                                ),
                                tint = if (earned) ZenStar else MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .size(if (earned) 42.dp else 34.dp)
                                    // Unearned stars stay put; earned ones pop in.
                                    .scale(if (earned) scaleAnim.value else 1f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatCell(
                            label = stringResource(id = R.string.level_complete_moves),
                            value = state.moves.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            label = stringResource(id = R.string.level_complete_par),
                            value = state.par.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            label = stringResource(id = R.string.level_complete_best),
                            value = state.bestMoves?.toString() ?: "-",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (state.isRecord) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = stringResource(id = R.string.level_complete_new_best),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }

                    if (finishedCampaign) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(id = R.string.campaign_finished_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    ZenButton(
                        text = stringResource(
                            id = when {
                                finishedCampaign -> R.string.level_complete_levels
                                state.nextLevelAvailable -> R.string.level_complete_next
                                else -> R.string.level_complete_unlock
                            },
                        ),
                        onClick = if (finishedCampaign) onLevels else onNext,
                        iconRes = if (finishedCampaign) R.drawable.ic_levels else R.drawable.ic_next,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ZenButton(
                            text = stringResource(id = R.string.level_complete_replay),
                            onClick = onReplay,
                            iconRes = R.drawable.ic_restart,
                            filled = false,
                            modifier = Modifier.weight(1f),
                            height = 50.dp,
                        )
                        ZenButton(
                            text = stringResource(id = R.string.level_complete_levels),
                            onClick = onLevels,
                            iconRes = R.drawable.ic_levels,
                            filled = false,
                            modifier = Modifier.weight(1f),
                            height = 50.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
