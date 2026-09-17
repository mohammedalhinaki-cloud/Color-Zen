package com.colorzen.puzzle.ui.shop

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.colorzen.puzzle.billing.BillingEvent
import com.colorzen.puzzle.billing.BillingManager
import com.colorzen.puzzle.billing.BillingStatus
import com.colorzen.puzzle.billing.ProductIds
import com.colorzen.puzzle.core.AppContainer
import com.colorzen.puzzle.core.Entitlements
import com.colorzen.puzzle.core.Sfx
import com.colorzen.puzzle.game.BoardGeometry
import com.colorzen.puzzle.game.BottleStyle
import com.colorzen.puzzle.game.LevelPlan
import com.colorzen.puzzle.game.LiquidPalette
import com.colorzen.puzzle.game.Palettes
import com.colorzen.puzzle.ui.components.SectionHeader
import com.colorzen.puzzle.ui.components.ZenButton
import com.colorzen.puzzle.ui.components.ZenCard
import com.colorzen.puzzle.ui.components.ZenDivider
import com.colorzen.puzzle.ui.components.ZenScreen
import com.colorzen.puzzle.ui.components.ZenTopBar
import com.colorzen.puzzle.ui.game.BoardPainting
import com.colorzen.puzzle.ui.nav.ZenNavigator
import com.colorzen.puzzle.ui.theme.ZenGlassDark
import com.colorzen.puzzle.ui.theme.ZenGlassLight
import com.colorzen.puzzle.ui.theme.ZenGlassStrokeDark
import com.colorzen.puzzle.ui.theme.ZenGlassStrokeLight
import com.colorzen.puzzle.ui.theme.ZenShineDark
import com.colorzen.puzzle.ui.theme.ZenShineLight
import kotlinx.coroutines.delay

private const val BANNER_VISIBLE_MS = 2800L

private enum class BannerKind { GRANTED, FAILED, RESTORED, NOTHING }

private class Banner(val kind: BannerKind, val productId: String? = null, val code: Int = 0)

/**
 * Shop screen.
 *
 * Every price comes from Google Play at runtime
 * (`ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`) - never from a
 * hard-coded constant - so the in-app price can never disagree with the store
 * listing. No ads, no subscriptions: six one-time products in total.
 */
@Composable
fun ShopScreen(container: AppContainer, navigator: ZenNavigator) {
    val store = container.playerStore
    val billing = container.billingManager
    val sound = container.soundManager

    val owned by store.ownedProducts.collectAsState()
    val hintCoins by store.hintCoins.collectAsState()
    val paletteId by store.paletteId.collectAsState()
    val bottleStyleId by store.bottleStyleId.collectAsState()
    val results by store.results.collectAsState()
    val billingStatus by billing.status.collectAsState()
    val event by billing.events.collectAsState()

    val activity = LocalContext.current as? Activity
    val ownsFullGame = Entitlements.ownsFullGame(owned)
    val ownsThemes = Entitlements.ownsThemes(owned)

    LaunchedEffect(Unit) { billing.start() }

    var banner by remember { mutableStateOf<Banner?>(null) }
    LaunchedEffect(event) {
        val current = event ?: return@LaunchedEffect
        billing.consumeEvent()
        banner = when (current) {
            BillingEvent.PurchaseCancelled -> null
            is BillingEvent.PurchaseFailed -> Banner(BannerKind.FAILED, code = current.code)
            is BillingEvent.Granted -> Banner(BannerKind.GRANTED, productId = current.productId)
            BillingEvent.Restored -> Banner(BannerKind.RESTORED)
            BillingEvent.NothingToRestore -> Banner(BannerKind.NOTHING)
        }
        if (banner != null) {
            delay(BANNER_VISIBLE_MS)
            banner = null
        }
    }

    ZenScreen {
        ZenTopBar(
            title = stringResource(id = R.string.shop_title),
            subtitle = stringResource(id = R.string.shop_subtitle),
            onBack = { navigator.back() },
        )

        banner?.let { current ->
            val text = when (current.kind) {
                BannerKind.GRANTED -> stringResource(
                    id = R.string.shop_event_granted,
                    productName(current.productId),
                )

                BannerKind.FAILED -> stringResource(id = R.string.shop_event_failed, current.code)
                BannerKind.RESTORED -> stringResource(id = R.string.shop_event_restored)
                BannerKind.NOTHING -> stringResource(id = R.string.shop_event_nothing)
            }
            InfoBanner(text = text, positive = current.kind == BannerKind.GRANTED)
        }

        if (billingStatus is BillingStatus.Unavailable) {
            InfoBanner(text = stringResource(id = R.string.shop_play_unavailable), positive = false)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "full_game") {
                HighlightCard(
                    iconRes = R.drawable.ic_trophy,
                    title = stringResource(id = R.string.shop_full_game_title),
                    body = stringResource(
                        id = R.string.shop_full_game_body,
                        priceOr(billing, ProductIds.FULL_GAME),
                    ),
                    price = billing.formattedPrice(ProductIds.FULL_GAME),
                    owned = ownsFullGame,
                    onBuy = { buy(activity, billing, ProductIds.FULL_GAME) },
                )
            }

            item(key = "packs") {
                ZenCard {
                    SectionHeader(
                        title = stringResource(id = R.string.shop_packs_title),
                        trailing = stringResource(
                            id = R.string.levels_progress,
                            results.size,
                            LevelPlan.TOTAL_LEVELS,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.shop_packs_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProductIds.levelPacks.forEachIndexed { index, productId ->
                        val packIndex = index + 1
                        val range = LevelPlan.levelsOfPack(packIndex)
                        Spacer(modifier = Modifier.height(14.dp))
                        if (index > 0) {
                            ZenDivider(modifier = Modifier.padding(bottom = 14.dp))
                        }
                        PurchaseRow(
                            title = stringResource(id = R.string.shop_pack_name, packIndex),
                            body = stringResource(
                                id = R.string.shop_pack_body,
                                range.first,
                                range.last,
                                LevelPlan.LEVELS_PER_PACK,
                            ),
                            price = billing.formattedPrice(productId),
                            owned = Entitlements.ownsPack(owned, packIndex),
                            onBuy = { buy(activity, billing, productId) },
                        )
                    }
                }
            }

            item(key = "hints") {
                ZenCard {
                    PurchaseRow(
                        iconRes = R.drawable.ic_hint,
                        title = stringResource(id = R.string.shop_hints_title),
                        body = stringResource(id = R.string.shop_hints_body, hintCoins),
                        price = billing.formattedPrice(ProductIds.HINT_PACK_10),
                        owned = false,
                        ownedLabel = null,
                        onBuy = { buy(activity, billing, ProductIds.HINT_PACK_10) },
                    )
                }
            }

            item(key = "themes") {
                ThemesCard(
                    palettes = Palettes.all,
                    selectedPalette = paletteId,
                    styles = BottleStyle.entries,
                    selectedStyle = bottleStyleId,
                    themesOwned = ownsThemes,
                    price = billing.formattedPrice(ProductIds.COLOR_THEMES),
                    onSelectPalette = { id ->
                        sound.play(Sfx.CLICK)
                        store.setPalette(id)
                    },
                    onSelectStyle = { id ->
                        sound.play(Sfx.CLICK)
                        store.setBottleStyle(id)
                    },
                    onBuy = { buy(activity, billing, ProductIds.COLOR_THEMES) },
                )
            }

            item(key = "restore") {
                ZenButton(
                    text = stringResource(id = R.string.shop_restore),
                    onClick = {
                        sound.play(Sfx.CLICK)
                        billing.restorePurchases()
                    },
                    iconRes = R.drawable.ic_restart,
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                )
            }

            item(key = "notes") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(id = R.string.shop_note_no_ads),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.shop_note_billing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun productName(productId: String?): String = when (productId) {
    ProductIds.FULL_GAME -> stringResource(id = R.string.shop_full_game_title)
    ProductIds.COLOR_THEMES -> stringResource(id = R.string.shop_themes_title)
    ProductIds.HINT_PACK_10 -> stringResource(id = R.string.shop_hints_title)
    ProductIds.LEVEL_PACK_1 -> stringResource(id = R.string.shop_pack_name, 1)
    ProductIds.LEVEL_PACK_2 -> stringResource(id = R.string.shop_pack_name, 2)
    ProductIds.LEVEL_PACK_3 -> stringResource(id = R.string.shop_pack_name, 3)
    else -> stringResource(id = R.string.shop_title)
}

@Composable
private fun priceOr(billing: BillingManager, productId: String): String =
    billing.formattedPrice(productId) ?: stringResource(id = R.string.shop_price_loading)

private fun buy(activity: Activity?, billing: BillingManager, productId: String) {
    if (activity == null) {
        billing.start()
        return
    }
    billing.purchase(activity, productId)
}

@Composable
private fun InfoBanner(text: String, positive: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (positive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (positive) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun HighlightCard(
    iconRes: Int,
    title: String,
    body: String,
    price: String?,
    owned: Boolean,
    onBuy: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (owned) {
                OwnedChip()
            } else {
                ZenButton(
                    text = price?.let { stringResource(id = R.string.shop_buy_for, it) }
                        ?: stringResource(id = R.string.shop_price_loading),
                    onClick = onBuy,
                    enabled = price != null,
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                )
            }
        }
    }
}

@Composable
private fun PurchaseRow(
    title: String,
    body: String,
    price: String?,
    owned: Boolean,
    onBuy: () -> Unit,
    iconRes: Int? = null,
    ownedLabel: String? = stringResource(id = R.string.shop_owned),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (owned && ownedLabel != null) {
            OwnedChip(label = ownedLabel)
        } else {
            ZenButton(
                text = price?.let { stringResource(id = R.string.shop_buy_for, it) }
                    ?: stringResource(id = R.string.shop_price_loading),
                onClick = onBuy,
                enabled = price != null,
                height = 42.dp,
            )
        }
    }
}

@Composable
private fun OwnedChip(label: String = stringResource(id = R.string.shop_owned)) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Colour palettes and bottle designs, with purchase gating for premium ones. */
@Composable
private fun ThemesCard(
    palettes: List<LiquidPalette>,
    selectedPalette: String,
    styles: List<BottleStyle>,
    selectedStyle: String,
    themesOwned: Boolean,
    price: String?,
    onSelectPalette: (String) -> Unit,
    onSelectStyle: (String) -> Unit,
    onBuy: () -> Unit,
) {
    ZenCard {
        SectionHeader(title = stringResource(id = R.string.shop_themes_title))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.shop_themes_body, price ?: "-"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(14.dp))
        palettes.forEach { palette ->
            val unlocked = !palette.premium || themesOwned
            PaletteRow(
                palette = palette,
                unlocked = unlocked,
                selected = palette.id == selectedPalette,
                onClick = { if (unlocked) onSelectPalette(palette.id) else onBuy() },
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        ZenDivider(modifier = Modifier.padding(vertical = 2.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(id = R.string.shop_bottle_styles),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            styles.forEach { style ->
                val unlocked = !style.premium || themesOwned
                BottleStyleTile(
                    style = style,
                    palette = Palettes.byId(selectedPalette),
                    unlocked = unlocked,
                    selected = style.id == selectedStyle,
                    onClick = { if (unlocked) onSelectStyle(style.id) else onBuy() },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!themesOwned) {
            Spacer(modifier = Modifier.height(16.dp))
            ZenButton(
                text = price?.let { stringResource(id = R.string.shop_buy_for, it) }
                    ?: stringResource(id = R.string.shop_price_loading),
                onClick = onBuy,
                enabled = price != null,
                iconRes = R.drawable.ic_palette,
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp,
            )
        }
    }
}

@Composable
private fun PaletteRow(
    palette: LiquidPalette,
    unlocked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                },
            )
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            palette.colors.take(6).forEach { value ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(value)),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(id = paletteNameRes(palette.id)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!unlocked) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lock),
                contentDescription = stringResource(id = R.string.shop_locked),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        } else if (selected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = stringResource(id = R.string.shop_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BottleStyleTile(
    style: BottleStyle,
    palette: LiquidPalette,
    unlocked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            BottleStylePreview(
                style = style,
                palette = palette,
                modifier = Modifier
                    .width(26.dp)
                    .height(52.dp),
            )
            if (!unlocked) {
                // Scrim instead of recolouring the palette: the preview always
                // shows the real design, so the shop is never misleading.
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.22f)),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(id = bottleStyleNameRes(style.id)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!unlocked) {
            Spacer(modifier = Modifier.height(2.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_lock),
                contentDescription = stringResource(id = R.string.shop_locked),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** One bottle drawn with the real board renderer, at preview scale. */
@Composable
fun BottleStylePreview(
    style: BottleStyle,
    palette: LiquidPalette,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val glassFill = if (dark) ZenGlassDark else ZenGlassLight
    val glassStroke = if (dark) ZenGlassStrokeDark else ZenGlassStrokeLight
    val shine = if (dark) ZenShineDark else ZenShineLight

    Canvas(modifier = modifier) {
        val geometry = BoardGeometry(
            bottleCount = 1,
            width = size.width,
            height = size.height,
            maxColumns = 1,
            bottleAspect = 0.44f,
            fillRatio = 0.92f,
        )
        val cell = geometry.cellAt(0) ?: return@Canvas
        BoardPainting.drawBottle(
            scope = this,
            geometry = geometry,
            rect = cell,
            segments = listOf(1, 1, 2, 3),
            capacity = LevelPlan.CAPACITY,
            palette = palette,
            style = style,
            glassFill = glassFill,
            glassStroke = glassStroke,
            shine = shine,
            rimColor = if (style == BottleStyle.CLASSIC || style == BottleStyle.FLASK) {
                glassStroke.copy(alpha = 0.85f)
            } else {
                Color.Transparent
            },
        )
    }
}

private fun paletteNameRes(id: String): Int = when (id) {
    "pastel" -> R.string.palette_pastel
    "sunset" -> R.string.palette_sunset
    "ocean" -> R.string.palette_ocean
    "candy" -> R.string.palette_candy
    "bloom" -> R.string.palette_bloom
    else -> R.string.palette_pastel
}

private fun bottleStyleNameRes(id: String): Int = when (id) {
    "classic" -> R.string.style_classic
    "rounded" -> R.string.style_rounded
    "flask" -> R.string.style_flask
    "prism" -> R.string.style_prism
    else -> R.string.style_classic
}
