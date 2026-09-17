package com.colorzen.puzzle.ui.levels

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.colorzen.puzzle.R
import com.colorzen.puzzle.billing.ProductIds
import com.colorzen.puzzle.core.AppContainer
import com.colorzen.puzzle.core.Entitlements
import com.colorzen.puzzle.core.LevelLock
import com.colorzen.puzzle.game.LevelPlan
import com.colorzen.puzzle.game.Palettes
import com.colorzen.puzzle.game.Tier
import com.colorzen.puzzle.ui.components.SectionHeader
import com.colorzen.puzzle.ui.components.StarRow
import com.colorzen.puzzle.ui.components.ZenButton
import com.colorzen.puzzle.ui.components.ZenCard
import com.colorzen.puzzle.ui.components.ZenPill
import com.colorzen.puzzle.ui.components.ZenScreen
import com.colorzen.puzzle.ui.components.ZenTopBar
import com.colorzen.puzzle.ui.nav.ZenNavigator

private const val TILES_PER_ROW = 5

/** Level selection: every one of the 120 levels, grouped by difficulty. */
@Composable
fun LevelSelectScreen(container: AppContainer, navigator: ZenNavigator) {
    val store = container.playerStore
    val billing = container.billingManager

    val results by store.results.collectAsState()
    val owned by store.ownedProducts.collectAsState()
    val paletteId by store.paletteId.collectAsState()
    val lastLevel by store.lastLevel.collectAsState()
    val savedGame by store.savedGame.collectAsState()
    val palette = remember(paletteId) { Palettes.byId(paletteId) }

    val activity = LocalContext.current as? Activity
    val totalStars = results.values.sumOf { it.stars }

    ZenScreen(accent = Color(palette.accent)) {
        ZenTopBar(
            title = stringResource(id = R.string.levels_title),
            subtitle = stringResource(
                id = R.string.levels_progress,
                results.size,
                LevelPlan.TOTAL_LEVELS,
            ),
            onBack = { navigator.back() },
            actions = {
                ZenPill(
                    text = totalStars.toString(),
                    iconRes = R.drawable.ic_star,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(LevelPlan.sections, key = { it.tier.id + it.fromLevel }) { section ->
                val packIndex = LevelPlan.packIndexFor(section.fromLevel)
                val packOwned = Entitlements.ownsPack(owned, packIndex)
                val packProductId = ProductIds.levelPacks.getOrNull(packIndex - 1)
                val completedInSection = (section.fromLevel..section.toLevel).count { it in results }

                ZenCard(contentPadding = PaddingValues(16.dp)) {
                    SectionHeader(
                        title = stringResource(id = tierLabelRes(section.tier)),
                        trailing = stringResource(
                            id = R.string.levels_section_progress,
                            completedInSection,
                            section.toLevel - section.fromLevel + 1,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            id = R.string.levels_section_shape,
                            section.fromLevel,
                            section.toLevel,
                            LevelPlan.bottlesFor(section.fromLevel),
                            LevelPlan.colorsFor(section.fromLevel),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!packOwned && packProductId != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        PackPurchaseBanner(
                            packIndex = packIndex,
                            productId = packProductId,
                            price = billing.formattedPrice(packProductId),
                            onBuy = {
                                if (activity != null) billing.purchase(activity, packProductId)
                            },
                            onOpenShop = { navigator.toShop() },
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    val levels = (section.fromLevel..section.toLevel).toList()
                    levels.chunked(TILES_PER_ROW).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { level ->
                                val lock = Entitlements.lockReason(owned, results.keys, level)
                                val isCurrent = level == (savedGame?.level ?: lastLevel)
                                LevelTile(
                                    level = level,
                                    stars = results[level]?.stars ?: 0,
                                    completed = level in results,
                                    lock = lock,
                                    isCurrent = isCurrent,
                                    dimmed = !packOwned,
                                    accent = Color(palette.accent),
                                    onClick = {
                                        when (lock) {
                                            LevelLock.Unlocked -> navigator.toGame(level)
                                            // Gated by progression: take the
                                            // player to the level they must
                                            // finish rather than doing nothing.
                                            LevelLock.PreviousIncomplete ->
                                                navigator.toGame(
                                                    Entitlements.nextPlayableLevel(results.keys),
                                                )

                                            is LevelLock.PackRequired -> navigator.toShop()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Keep tiles the same width on a short final row.
                            repeat(TILES_PER_ROW - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            item {
                FullGameCard(
                    owned = Entitlements.ownsFullGame(owned),
                    price = billing.formattedPrice(ProductIds.FULL_GAME),
                    onBuy = {
                        if (activity != null) billing.purchase(activity, ProductIds.FULL_GAME)
                    },
                    onOpenShop = { navigator.toShop() },
                )
            }
        }
    }
}

@Composable
private fun LevelTile(
    level: Int,
    stars: Int,
    completed: Boolean,
    lock: LevelLock,
    isCurrent: Boolean,
    dimmed: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locked = lock != LevelLock.Unlocked
    val shape = RoundedCornerShape(16.dp)
    val background = when {
        completed -> accent.copy(alpha = 0.16f)
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        locked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        completed -> accent.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = if (locked) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dimmed) 0.45f else 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .aspectRatio(0.80f)
            .clip(shape)
            .background(background)
            .border(width = if (isCurrent) 1.6.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (locked && !completed) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lock),
                contentDescription = stringResource(id = R.string.levels_locked),
                tint = contentColor,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.height(3.dp))
        }
        Text(
            text = level.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (completed) {
            StarRow(stars = stars, size = 9.dp)
        } else {
            Box(modifier = Modifier.height(9.dp))
        }
    }
}

@Composable
private fun PackPurchaseBanner(
    packIndex: Int,
    productId: String,
    price: String?,
    onBuy: () -> Unit,
    onOpenShop: () -> Unit,
) {
    val range = LevelPlan.levelsOfPack(packIndex)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(id = R.string.levels_pack_title, packIndex),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(
                    id = R.string.levels_pack_body,
                    range.first,
                    range.last,
                    price ?: stringResource(id = R.string.shop_price_loading),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZenButton(
                    text = price?.let { stringResource(id = R.string.shop_buy_for, it) }
                        ?: stringResource(id = R.string.shop_view),
                    onClick = onBuy,
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                )
                ZenButton(
                    text = stringResource(id = R.string.shop_view),
                    onClick = onOpenShop,
                    filled = false,
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun FullGameCard(owned: Boolean, price: String?, onBuy: () -> Unit, onOpenShop: () -> Unit) {
    ZenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_palette),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.shop_full_game_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (owned) {
                        stringResource(id = R.string.shop_owned)
                    } else {
                        stringResource(
                            id = R.string.shop_full_game_body,
                            price ?: stringResource(id = R.string.shop_price_loading),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!owned) {
                Spacer(modifier = Modifier.width(10.dp))
                ZenButton(
                    text = price?.let { stringResource(id = R.string.shop_buy_for, it) }
                        ?: stringResource(id = R.string.shop_view),
                    onClick = if (price != null) onBuy else onOpenShop,
                    height = 42.dp,
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = stringResource(id = R.string.shop_owned),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun tierLabelRes(tier: Tier): Int = when (tier) {
    Tier.BEGINNER -> R.string.tier_beginner
    Tier.EASY -> R.string.tier_easy
    Tier.MEDIUM -> R.string.tier_medium
    Tier.HARD -> R.string.tier_hard
    Tier.EXPERT -> R.string.tier_expert
    Tier.MASTER -> R.string.tier_master
}
