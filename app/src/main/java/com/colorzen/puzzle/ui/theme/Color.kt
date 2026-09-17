package com.colorzen.puzzle.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * App chrome colours.
 *
 * These are intentionally *not* the liquid colours - the game palette lives in
 * `game/Palettes.kt` so that a purchased theme recolours the puzzle without
 * touching the UI chrome (and vice versa).
 */

// Light: warm paper, periwinkle accent, mint support.
val ZenPaper = Color(0xFFFAF7F3)
val ZenSurfaceLight = Color(0xFFFFFFFF)
val ZenSurfaceVariantLight = Color(0xFFF1ECE5)
val ZenPrimaryLight = Color(0xFF6E7BD2)
val ZenPrimaryContainerLight = Color(0xFFE3E6FB)
val ZenSecondaryLight = Color(0xFF63A98F)
val ZenSecondaryContainerLight = Color(0xFFDCF0E7)
val ZenTertiaryLight = Color(0xFFC98BB0)
val ZenInkLight = Color(0xFF33353F)
val ZenInkVariantLight = Color(0xFF6B6E7B)
val ZenOutlineLight = Color(0xFFDCD6CD)

// Dark: deep slate, the same hues lifted for contrast.
val ZenBackgroundDark = Color(0xFF14161B)
val ZenSurfaceDark = Color(0xFF1D2027)
val ZenSurfaceVariantDark = Color(0xFF262A33)
val ZenPrimaryDark = Color(0xFF9AA6F2)
val ZenPrimaryContainerDark = Color(0xFF2E3459)
val ZenSecondaryDark = Color(0xFF7FC8A9)
val ZenSecondaryContainerDark = Color(0xFF24463A)
val ZenTertiaryDark = Color(0xFFE0A0C8)
val ZenInkDark = Color(0xFFE8EAF0)
val ZenInkVariantDark = Color(0xFFA6AAB8)
val ZenOutlineDark = Color(0xFF363B46)

/** Shared accents used by the board renderer and the completion animation. */
val ZenGlassLight = Color(0x22000000)
val ZenGlassDark = Color(0x2EFFFFFF)
val ZenGlassStrokeLight = Color(0x59363B46)
val ZenGlassStrokeDark = Color(0x66D7DCE6)
val ZenShineLight = Color(0x59FFFFFF)
val ZenShineDark = Color(0x33FFFFFF)
val ZenSuccess = Color(0xFF6FCF97)
val ZenStar = Color(0xFFF6C453)
val ZenStarOff = Color(0x33000000)
