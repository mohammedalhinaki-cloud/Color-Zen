package com.colorzen.puzzle.ui.menu

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.colorzen.puzzle.R
import com.colorzen.puzzle.core.AppConfig
import com.colorzen.puzzle.core.AppContainer
import com.colorzen.puzzle.game.BoardGeometry
import com.colorzen.puzzle.game.BottleStyle
import com.colorzen.puzzle.game.LevelPlan
import com.colorzen.puzzle.game.LiquidPalette
import com.colorzen.puzzle.game.Palettes
import com.colorzen.puzzle.ui.components.ZenButton
import com.colorzen.puzzle.ui.components.ZenRow
import com.colorzen.puzzle.ui.components.ZenScreen
import com.colorzen.puzzle.ui.game.BoardPainting
import com.colorzen.puzzle.ui.nav.ZenNavigator
import com.colorzen.puzzle.ui.theme.ZenGlassDark
import com.colorzen.puzzle.ui.theme.ZenGlassLight
import com.colorzen.puzzle.ui.theme.ZenGlassStrokeDark
import com.colorzen.puzzle.ui.theme.ZenGlassStrokeLight
import com.colorzen.puzzle.ui.theme.ZenShineDark
import com.colorzen.puzzle.ui.theme.ZenShineLight
import kotlin.math.PI
import kotlin.math.sin

/** Main menu. */
@Composable
fun MenuScreen(container: AppContainer, navigator: ZenNavigator) {
    val store = container.playerStore
    val completed by store.results.collectAsState()
    val lastLevel by store.lastLevel.collectAsState()
    val savedGame by store.savedGame.collectAsState()
    val paletteId by store.paletteId.collectAsState()
    val hintCoins by store.hintCoins.collectAsState()
    val palette = remember(paletteId) { Palettes.byId(paletteId) }

    // Resume an unfinished board if there is one, otherwise the first level the
    // player has not completed yet (falling back to the last level played).
    val resumeLevel = savedGame?.level ?: (1..LevelPlan.TOTAL_LEVELS)
        .firstOrNull { it !in completed && store.isLevelUnlocked(it) }
        ?: lastLevel
    val stars = completed.values.sumOf { it.stars }

    ZenScreen(accent = Color(palette.accent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            ZenLogoMark(
                palette = palette,
                modifier = Modifier
                    .width(210.dp)
                    .height(150.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.menu_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(26.dp))
            ZenButton(
                text = if (savedGame != null) {
                    stringResource(id = R.string.menu_resume, resumeLevel)
                } else {
                    stringResource(id = R.string.menu_play_level, resumeLevel)
                },
                onClick = { navigator.toGame(resumeLevel) },
                iconRes = R.drawable.ic_play,
                modifier = Modifier.fillMaxWidth(),
                height = 60.dp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ZenButton(
                text = stringResource(id = R.string.menu_levels),
                onClick = { navigator.toLevels() },
                iconRes = R.drawable.ic_levels,
                filled = false,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
            )

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProgressStat(
                    icon = R.drawable.ic_check,
                    value = "${completed.size}",
                    label = stringResource(id = R.string.menu_stat_levels, LevelPlan.TOTAL_LEVELS),
                    modifier = Modifier.weight(1f),
                )
                ProgressStat(
                    icon = R.drawable.ic_star,
                    value = "$stars",
                    label = stringResource(id = R.string.menu_stat_stars),
                    modifier = Modifier.weight(1f),
                )
                ProgressStat(
                    icon = R.drawable.ic_coin,
                    value = hintCoins.toString(),
                    label = stringResource(id = R.string.menu_stat_hints),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            ZenRow(
                title = stringResource(id = R.string.menu_shop),
                subtitle = stringResource(id = R.string.menu_shop_subtitle),
                iconRes = R.drawable.ic_shop,
                onClick = { navigator.toShop() },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ZenRow(
                title = stringResource(id = R.string.menu_settings),
                subtitle = stringResource(id = R.string.menu_settings_subtitle),
                iconRes = R.drawable.ic_settings,
                onClick = { navigator.toSettings() },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ZenRow(
                title = stringResource(id = R.string.menu_privacy),
                subtitle = stringResource(id = R.string.menu_privacy_subtitle),
                iconRes = R.drawable.ic_shield,
                onClick = { navigator.toPrivacy() },
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(id = R.string.menu_no_ads),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(id = R.string.app_version, AppConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ProgressStat(
    icon: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Three gently bobbing bottles - the app's visual signature, drawn with exactly
 * the same renderer as the game board so the menu always matches gameplay.
 */
@Composable
fun ZenLogoMark(palette: LiquidPalette, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val glassFill = if (dark) ZenGlassDark else ZenGlassLight
    val glassStroke = if (dark) ZenGlassStrokeDark else ZenGlassStrokeLight
    val shine = if (dark) ZenShineDark else ZenShineLight

    val transition = rememberInfiniteTransition(label = "logoBob")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "logoBobPhase",
    )

    val logoBottles = remember {
        listOf(
            listOf(1, 1, 3, 2),
            listOf(2, 2, 1, 3),
            listOf(3, 3, 2, 1),
        )
    }

    Canvas(modifier = modifier) {
        val geometry = BoardGeometry(
            bottleCount = logoBottles.size,
            width = size.width,
            height = size.height,
            maxColumns = 3,
            bottleAspect = 0.36f,
            fillRatio = 0.62f,
        )
        logoBottles.forEachIndexed { index, segments ->
            val cell = geometry.cellAt(index) ?: return@forEachIndexed
            val offset = sin((phase + index * 0.33f) * 2f * PI.toFloat()) * size.height * 0.025f
            BoardPainting.drawBottle(
                scope = this,
                geometry = geometry,
                rect = cell.translatedBy(0f, offset),
                segments = segments,
                capacity = LevelPlan.CAPACITY,
                palette = palette,
                style = BottleStyle.CLASSIC,
                glassFill = glassFill,
                glassStroke = glassStroke,
                shine = shine,
                rimColor = glassStroke.copy(alpha = 0.85f),
            )
        }
    }
}
