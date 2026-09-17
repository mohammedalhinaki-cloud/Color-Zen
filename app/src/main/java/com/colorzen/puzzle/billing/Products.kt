package com.colorzen.puzzle.billing

import com.colorzen.puzzle.game.LevelPlan

/**
 * Every Google Play product this app sells.
 *
 * These IDs must be created in Play Console -> Monetise -> Products ->
 * In-app products with the exact same strings, activated, and priced as listed.
 * Nothing is hard-coded about prices in the UI: the shop always renders
 * `ProductDetails.oneTimePurchaseOfferDetails.formattedPrice` from Play, so the
 * store listing and the in-app price can never disagree.
 *
 * There are no subscriptions and no ads in this app.
 */
object ProductIds {

    // -- Non-consumable: level packs (30 levels each) -----------------------
    /** Levels 31-60. */
    const val LEVEL_PACK_1 = "level_pack_1"

    /** Levels 61-90. */
    const val LEVEL_PACK_2 = "level_pack_2"

    /** Levels 91-120. */
    const val LEVEL_PACK_3 = "level_pack_3"

    /** Unlocks all 120 levels forever (supersedes the individual packs). */
    const val FULL_GAME = "full_game"

    /** Unlocks the extra colour palettes and bottle designs. */
    const val COLOR_THEMES = "color_themes"

    // -- Consumable ---------------------------------------------------------
    /** 10 hints. Granted on purchase, then consumed one hint at a time. */
    const val HINT_PACK_10 = "hint_pack_10"

    val levelPacks: List<String> = listOf(LEVEL_PACK_1, LEVEL_PACK_2, LEVEL_PACK_3)

    val nonConsumables: List<String> = levelPacks + FULL_GAME + COLOR_THEMES

    val consumables: List<String> = listOf(HINT_PACK_10)

    /** Everything the app queries from Play at startup. */
    val all: List<String> = nonConsumables + consumables
}

/** Shop-facing description of one product. */
data class ProductInfo(
    val id: String,
    val kind: ProductKind,
    /** Pack index (1..3) for level packs, otherwise null. */
    val packIndex: Int? = null,
    val levelRange: IntRange? = null,
    val hints: Int = 0,
)

enum class ProductKind { LEVEL_PACK, FULL_GAME, THEMES, HINTS }

object Catalogue {

    val items: List<ProductInfo> = buildList {
        ProductIds.levelPacks.forEachIndexed { index, id ->
            val packIndex = index + 1
            add(
                ProductInfo(
                    id = id,
                    kind = ProductKind.LEVEL_PACK,
                    packIndex = packIndex,
                    levelRange = LevelPlan.levelsOfPack(packIndex),
                ),
            )
        }
        add(ProductInfo(id = ProductIds.FULL_GAME, kind = ProductKind.FULL_GAME))
        add(ProductInfo(id = ProductIds.COLOR_THEMES, kind = ProductKind.THEMES))
        add(ProductInfo(id = ProductIds.HINT_PACK_10, kind = ProductKind.HINTS, hints = 10))
    }

    fun byId(id: String): ProductInfo? = items.firstOrNull { it.id == id }

    /** Hints delivered by a consumable product. */
    fun hintAmount(productId: String): Int =
        items.firstOrNull { it.id == productId }?.hints ?: 0

    /** Suggested Play Console prices - the real price always comes from Play. */
    val suggestedPrices: Map<String, String> = mapOf(
        ProductIds.LEVEL_PACK_1 to "0.99",
        ProductIds.LEVEL_PACK_2 to "0.99",
        ProductIds.LEVEL_PACK_3 to "0.99",
        ProductIds.HINT_PACK_10 to "0.99",
        ProductIds.COLOR_THEMES to "1.99",
        ProductIds.FULL_GAME to "2.99",
    )
}
