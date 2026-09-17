package com.colorzen.puzzle.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.colorzen.puzzle.game.BoardGeometry
import com.colorzen.puzzle.game.BottleStyle
import com.colorzen.puzzle.game.GameRules
import com.colorzen.puzzle.game.LiquidPalette
import com.colorzen.puzzle.ui.theme.ZenGlassDark
import com.colorzen.puzzle.ui.theme.ZenGlassLight
import com.colorzen.puzzle.ui.theme.ZenGlassStrokeDark
import com.colorzen.puzzle.ui.theme.ZenGlassStrokeLight
import com.colorzen.puzzle.ui.theme.ZenShineDark
import com.colorzen.puzzle.ui.theme.ZenShineLight
import com.colorzen.puzzle.ui.theme.ZenSuccess
import kotlin.math.floor
import kotlin.math.max

/** A pour the board is currently animating; the model has not committed it yet. */
data class PendingPour(
    val token: Long,
    val from: Int,
    val to: Int,
    val count: Int,
)

/** Pour animation timeline, as fractions of the total duration. */
private object PourPhase {
    const val TILT_END = 0.18f
    const val TRANSFER_END = 0.78f
    const val MAX_TILT_DEGREES = 24f
}

private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)
private fun easeInOutQuad(t: Float): Float = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f

/**
 * The bottle board: one Canvas, one coordinate space.
 *
 * Bottles, liquid, the selection lift, the pour stream, hint glow, the invalid
 * tap shake and the completion confetti are all painted here so they can never
 * drift out of sync with each other.
 *
 * @param bottles committed state, bottom -> top per bottle.
 * @param pour in-flight pour, or null. While non-null, [bottles] is still the
 *   pre-pour state and the renderer interpolates.
 * @param pourProgress 0..1 timeline position of [pour].
 * @param celebration 0..1 confetti progress; 0 or 1 means "not celebrating".
 */
@Composable
fun BottleBoard(
    bottles: List<List<Int>>,
    capacity: Int,
    palette: LiquidPalette,
    style: BottleStyle,
    selectedIndex: Int,
    hint: GameRules.Move?,
    completedBottles: Set<Int>,
    pour: PendingPour?,
    pourProgress: Float,
    celebration: Float,
    invalidToken: Long,
    invalidIndex: Int,
    onBottleTap: (Int) -> Unit,
    boardDescription: String,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val bottleCount = bottles.size

    val geometry = remember(bottleCount, canvasSize.width, canvasSize.height) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && bottleCount > 0) {
            BoardGeometry(bottleCount, canvasSize.width.toFloat(), canvasSize.height.toFloat())
        } else {
            null
        }
    }

    val dark = isSystemInDarkTheme()
    val glassFill = if (dark) ZenGlassDark else ZenGlassLight
    val glassStroke = if (dark) ZenGlassStrokeDark else ZenGlassStrokeLight
    val shine = if (dark) ZenShineDark else ZenShineLight
    val accent = Color(palette.accent)
    val rimColor = glassStroke.copy(alpha = 0.85f)
    val shadowColor = if (dark) Color.Black.copy(alpha = 0.28f) else Color(0xFF3A3A44).copy(alpha = 0.10f)

    // Gentle pulse on the selected bottle and on hint targets.
    val pulseTransition = rememberInfiniteTransition(label = "boardPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "boardPulseValue",
    )

    val shake = remember { Animatable(0f) }
    LaunchedEffect(invalidToken) {
        if (invalidToken == 0L) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(targetValue = 1f, animationSpec = tween(45))
        shake.animateTo(targetValue = -1f, animationSpec = tween(55))
        shake.animateTo(targetValue = 0.6f, animationSpec = tween(45))
        shake.animateTo(targetValue = 0f, animationSpec = tween(55))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { canvasSize = it }
            .semantics { contentDescription = boardDescription },
    ) {
        val layout = geometry
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(layout) {
                    detectTapGestures { offset ->
                        val current = layout ?: return@detectTapGestures
                        val index = current.indexAt(offset.x, offset.y)
                        if (index >= 0) onBottleTap(index)
                    }
                },
        ) {
            if (layout == null) return@Canvas

            val pourState = computePourFrame(pour, pourProgress)
            val movedSegments: List<Int> = if (pour != null) {
                bottles.getOrNull(pour.from)?.takeLast(pour.count) ?: emptyList()
            } else {
                emptyList()
            }

            for (index in bottles.indices) {
                val rect = layout.cellAt(index) ?: continue

                // Liquid shown for this bottle, accounting for an in-flight pour.
                var segments = bottles[index]
                if (pour != null && pourState != null) {
                    when (index) {
                        pour.from -> segments =
                            segments.subList(0, max(0, segments.size - pourState.transferred))

                        pour.to -> segments =
                            segments + movedSegments.take(pourState.transferred)
                    }
                }

                val isSelected = index == selectedIndex
                val isHintSource = hint != null && hint.from == index
                val isHintTarget = hint != null && hint.to == index
                val isCompleted = index in completedBottles
                val isShaking = index == invalidIndex && shake.value != 0f

                val haloColor = when {
                    isSelected -> accent
                    isHintSource || isHintTarget -> ZenSuccess
                    isCompleted -> ZenSuccess
                    else -> Color.Transparent
                }
                val haloStrength = when {
                    isSelected -> 0.55f + pulse * 0.45f
                    isHintSource || isHintTarget -> 0.45f + pulse * 0.55f
                    isCompleted && celebration > 0f -> 0.35f + pulse * 0.4f
                    isCompleted -> 0.30f
                    else -> 0f
                }

                // Soft contact shadow, drawn before the bottle so it sits behind.
                drawOval(
                    color = shadowColor,
                    topLeft = Offset(
                        rect.centerX - rect.width * 0.40f,
                        rect.bottom - rect.height * 0.012f,
                    ),
                    size = Size(rect.width * 0.80f, rect.height * 0.035f),
                )

                BoardPainting.drawBottle(
                    scope = this,
                    geometry = layout,
                    rect = rect,
                    segments = segments,
                    capacity = capacity,
                    palette = palette,
                    style = style,
                    glassFill = glassFill,
                    glassStroke = glassStroke,
                    shine = shine,
                    liftPx = if (isSelected) rect.height * 0.085f else 0f,
                    tiltDegrees = if (pour != null && pourState != null && index == pour.from) {
                        pourState.tiltDegrees
                    } else {
                        0f
                    },
                    extraScale = when {
                        isSelected -> 1.045f
                        pour != null && index == pour.to -> 1.015f
                        else -> 1f
                    },
                    haloColor = haloColor,
                    haloStrength = haloStrength,
                    offsetX = if (isShaking) shake.value * rect.width * 0.14f else 0f,
                    rimColor = if (style == BottleStyle.CLASSIC || style == BottleStyle.FLASK) {
                        rimColor
                    } else {
                        Color.Transparent
                    },
                )

                // Pour stream, drawn after both bottles so it overlays them.
                if (pour != null && pourState != null && index == pour.from && pourState.transferFraction > 0f) {
                    val toRect = layout.cellAt(pour.to)
                    val colourId = movedSegments.firstOrNull() ?: 0
                    if (toRect != null && colourId > 0) {
                        BoardPainting.drawPourStream(
                            scope = this,
                            geometry = layout,
                            from = rect,
                            to = toRect,
                            color = Color(palette.colorForId(colourId)),
                            fraction = pourState.transferFraction,
                            targetFilled = (bottles[pour.to].size + pourState.transferred)
                                .coerceAtMost(capacity),
                            capacity = capacity,
                            tiltDegrees = pourState.tiltDegrees,
                        )
                    }
                }
            }

            BoardPainting.drawCelebration(
                scope = this,
                width = size.width,
                height = size.height,
                palette = palette,
                progress = celebration,
                accent = accent,
            )
        }
    }
}

/** Interpolated frame of a pour animation. */
internal class PourFrame(
    val tiltDegrees: Float,
    val transferred: Int,
    val transferFraction: Float,
)

/**
 * Maps a 0..1 timeline position onto tilt, how many segments have landed and
 * where the droplet is. Pure function - easy to test, no Compose state.
 */
internal fun computePourFrame(pour: PendingPour?, progress: Float): PourFrame? {
    if (pour == null) return null
    val p = progress.coerceIn(0f, 1f)

    val tiltIn = easeOutCubic((p / PourPhase.TILT_END).coerceIn(0f, 1f))
    val tiltOut = easeInOutQuad(
        ((p - PourPhase.TRANSFER_END) / (1f - PourPhase.TRANSFER_END)).coerceIn(0f, 1f),
    )
    val tilt = PourPhase.MAX_TILT_DEGREES * tiltIn * (1f - tiltOut)

    val transferT = ((p - PourPhase.TILT_END) / (PourPhase.TRANSFER_END - PourPhase.TILT_END))
        .coerceIn(0f, 1f)
    val eased = easeInOutQuad(transferT)
    val movedFloat = eased * pour.count
    val transferred = floor(movedFloat).toInt().coerceIn(0, pour.count)
    val fraction = if (pour.count == 0) 0f else movedFloat / pour.count

    return PourFrame(tiltDegrees = tilt, transferred = transferred, transferFraction = fraction)
}
