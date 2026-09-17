package com.colorzen.puzzle.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = ZenPrimaryLight,
    onPrimary = ZenSurfaceLight,
    primaryContainer = ZenPrimaryContainerLight,
    onPrimaryContainer = ZenPrimaryLight,
    secondary = ZenSecondaryLight,
    onSecondary = ZenSurfaceLight,
    secondaryContainer = ZenSecondaryContainerLight,
    onSecondaryContainer = ZenSecondaryLight,
    tertiary = ZenTertiaryLight,
    onTertiary = ZenSurfaceLight,
    background = ZenPaper,
    onBackground = ZenInkLight,
    surface = ZenSurfaceLight,
    onSurface = ZenInkLight,
    surfaceVariant = ZenSurfaceVariantLight,
    onSurfaceVariant = ZenInkVariantLight,
    outline = ZenOutlineLight,
    outlineVariant = ZenSurfaceVariantLight,
    inverseSurface = ZenInkLight,
    inverseOnSurface = ZenPaper,
    inversePrimary = ZenPrimaryDark,
    error = ZenTertiaryLight,
    onError = ZenSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = ZenPrimaryDark,
    onPrimary = ZenBackgroundDark,
    primaryContainer = ZenPrimaryContainerDark,
    onPrimaryContainer = ZenPrimaryDark,
    secondary = ZenSecondaryDark,
    onSecondary = ZenBackgroundDark,
    secondaryContainer = ZenSecondaryContainerDark,
    onSecondaryContainer = ZenSecondaryDark,
    tertiary = ZenTertiaryDark,
    onTertiary = ZenBackgroundDark,
    background = ZenBackgroundDark,
    onBackground = ZenInkDark,
    surface = ZenSurfaceDark,
    onSurface = ZenInkDark,
    surfaceVariant = ZenSurfaceVariantDark,
    onSurfaceVariant = ZenInkVariantDark,
    outline = ZenOutlineDark,
    outlineVariant = ZenSurfaceVariantDark,
    inverseSurface = ZenInkDark,
    inverseOnSurface = ZenBackgroundDark,
    inversePrimary = ZenPrimaryLight,
    error = ZenTertiaryDark,
    onError = ZenBackgroundDark,
)

/** Generously rounded - the whole visual language is soft geometry. */
val ZenShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Material 3 theme for the whole app.
 *
 * Status/navigation bar appearance is handled by `enableEdgeToEdge()` in
 * [com.colorzen.puzzle.MainActivity], which adapts to light and dark
 * automatically, so nothing here touches the window.
 */
@Composable
fun ColorZenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ZenTypography,
        shapes = ZenShapes,
        content = content,
    )
}
