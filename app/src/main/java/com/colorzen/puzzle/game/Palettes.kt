package com.colorzen.puzzle.game

/**
 * Bottle silhouettes drawn by the board renderer.
 *
 * All four keep a constant body width so that one liquid segment always maps to
 * the same height - only the bottom, shoulders and glass detailing change. That
 * keeps the "how much is in this bottle" read honest across themes.
 */
enum class BottleStyle(val id: String, val premium: Boolean) {
    CLASSIC("classic", premium = false),
    ROUNDED("rounded", premium = true),
    FLASK("flask", premium = true),
    PRISM("prism", premium = true);

    companion object {
        fun fromId(id: String): BottleStyle =
            entries.firstOrNull { it.id == id } ?: CLASSIC

        val free: List<BottleStyle> get() = entries.filter { !it.premium }
    }
}

/**
 * A colour set for the liquids.
 *
 * Every palette ships 11 colours because the Master tier (levels 106-120) uses
 * 11 distinct colours at once. Colours are stored as 0xAARRGGBB longs so this
 * file stays free of Compose types and remains unit-testable; the UI converts
 * with `androidx.compose.ui.graphics.Color(value)`.
 *
 * Colours are ordered by hue and alternate in luminance so that adjacent
 * segments stay separable for players with reduced colour vision.
 */
data class LiquidPalette(
    val id: String,
    val premium: Boolean,
    val colors: List<Long>,
    /** Used for the level-complete glow and the selected-bottle halo. */
    val accent: Long,
) {
    fun color(index: Int): Long = colors.getOrElse(index) { colors.last() }

    /** Colour ids in level data are 1-based. */
    fun colorForId(colourId: Int): Long = color(colourId - 1)
}

object Palettes {

    val PASTEL = LiquidPalette(
        id = "pastel",
        premium = false,
        accent = 0xFF8FA6E8,
        colors = listOf(
            0xFFF28BA8, // rose
            0xFFF6A96B, // peach
            0xFFF2D06B, // butter
            0xFFA8D5A2, // sage
            0xFF7FC8A9, // mint
            0xFF8EC9E8, // sky
            0xFF8FA6E8, // periwinkle
            0xFFB49BE0, // lavender
            0xFFE0A0C8, // orchid
            0xFFC9A88A, // sand
            0xFF9AA7B4, // slate
        ),
    )

    private val SUNSET = LiquidPalette(
        id = "sunset",
        premium = true,
        accent = 0xFFF2825C,
        colors = listOf(
            0xFFE8615A, // coral red
            0xFFF2825C, // tangerine
            0xFFF5A65B, // amber
            0xFFF2C57C, // golden
            0xFFD96C8F, // raspberry
            0xFFA85C9E, // plum
            0xFF7C6BAF, // violet
            0xFF5F86C7, // dusk blue
            0xFFC9705F, // terracotta
            0xFFE0A96D, // honey
            0xFF8E6F9E, // mauve
        ),
    )

    private val OCEAN = LiquidPalette(
        id = "ocean",
        premium = true,
        accent = 0xFF4FA3D1,
        colors = listOf(
            0xFF4FA3D1, // azure
            0xFF62C4C1, // teal
            0xFF7FD1A6, // seafoam
            0xFFA7D8F0, // pale blue
            0xFF3E7CB1, // deep blue
            0xFF5B8FA8, // steel
            0xFF86BAA5, // eucalyptus
            0xFFB9D9E8, // ice
            0xFF4E8098, // slate blue
            0xFF78A2CC, // cornflower
            0xFF9CC7C2, // mist
        ),
    )

    private val CANDY = LiquidPalette(
        id = "candy",
        premium = true,
        accent = 0xFFFF6F91,
        colors = listOf(
            0xFFFF6F91, // pink
            0xFFFF9671, // coral
            0xFFFFC75F, // yellow
            0xFFA0E7A5, // mint green
            0xFF6BCB77, // grass
            0xFF4D96FF, // blue
            0xFF9B72F2, // purple
            0xFFFF7FB5, // bubblegum
            0xFFFFD93D, // sunny
            0xFF6EE7DB, // aqua
            0xFFC4A1FF, // lilac
        ),
    )

    private val BLOOM = LiquidPalette(
        id = "bloom",
        premium = true,
        accent = 0xFFE27396,
        colors = listOf(
            0xFFE27396, // rose madder
            0xFFE8A87C, // apricot
            0xFFD4C36A, // olive gold
            0xFF8FBC8F, // sea green
            0xFF6B9080, // fern
            0xFFA26769, // clay
            0xFFC38D9E, // mauve pink
            0xFF85A9C4, // grey blue
            0xFFD2B48C, // tan
            0xFF9C6644, // umber
            0xFFB5C99A, // moss
        ),
    )

    val all: List<LiquidPalette> = listOf(PASTEL, SUNSET, OCEAN, CANDY, BLOOM)

    val default: LiquidPalette = PASTEL

    val premiumCount: Int get() = all.count { it.premium }

    fun byId(id: String?): LiquidPalette =
        all.firstOrNull { it.id == id } ?: default

    fun styleById(id: String?): BottleStyle = BottleStyle.fromId(id ?: BottleStyle.CLASSIC.id)
}
