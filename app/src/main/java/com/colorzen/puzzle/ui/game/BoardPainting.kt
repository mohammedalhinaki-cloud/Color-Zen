package com.colorzen.puzzle.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.colorzen.puzzle.game.BoardGeometry
import com.colorzen.puzzle.game.BottleStyle
import com.colorzen.puzzle.game.CellRect
import com.colorzen.puzzle.game.LiquidPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * All board painting, expressed as [DrawScope] helpers with no Composable
 * state. Keeping it free of composition means the renderer can be reasoned
 * about (and its geometry unit-tested) purely in terms of rectangles.
 *
 * Visual language: simple geometric bottles, flat soft colours, one glass
 * highlight, no skeuomorphic noise.
 */
internal object BoardPainting {

    /** Glass wall thickness for a bottle of this width. */
    fun wall(rect: CellRect): Float = (rect.width * 0.055f).coerceAtLeast(2f)

    // ---------------------------------------------------------------- shapes

    /** Outer silhouette of a bottle. */
    fun bottlePath(rect: CellRect, style: BottleStyle): Path = when (style) {
        BottleStyle.CLASSIC -> roundedBody(rect, top = 0.10f, bottom = 0.22f)
        BottleStyle.ROUNDED -> roundedBody(rect, top = 0.18f, bottom = 0.46f)
        BottleStyle.PRISM -> prismBody(rect)
        BottleStyle.FLASK -> flaskBody(rect)
    }

    private fun roundedBody(rect: CellRect, top: Float, bottom: Float): Path {
        val width = rect.width
        return Path().apply {
            addRoundRect(
                RoundRect(
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom,
                    topLeftCornerRadius = CornerRadius(width * top, width * top),
                    topRightCornerRadius = CornerRadius(width * top, width * top),
                    bottomRightCornerRadius = CornerRadius(width * bottom, width * bottom),
                    bottomLeftCornerRadius = CornerRadius(width * bottom, width * bottom),
                ),
            )
        }
    }

    /** Octagonal body: chamfered corners read as cut glass. */
    private fun prismBody(rect: CellRect): Path {
        val cut = rect.width * 0.22f
        return Path().apply {
            moveTo(rect.left + cut, rect.top)
            lineTo(rect.right - cut, rect.top)
            lineTo(rect.right, rect.top + cut)
            lineTo(rect.right, rect.bottom - cut)
            lineTo(rect.right - cut, rect.bottom)
            lineTo(rect.left + cut, rect.bottom)
            lineTo(rect.left, rect.bottom - cut)
            lineTo(rect.left, rect.top + cut)
            close()
        }
    }

    /** Narrow neck that flares into full-width shoulders. */
    private fun flaskBody(rect: CellRect): Path {
        val neckHalf = rect.width * 0.30f
        val centerX = rect.centerX
        val neckBottom = rect.top + rect.height * 0.13f
        val shoulderBottom = rect.top + rect.height * 0.24f
        val baseCut = rect.width * 0.16f
        return Path().apply {
            moveTo(centerX - neckHalf, rect.top)
            lineTo(centerX - neckHalf, neckBottom)
            cubicTo(
                centerX - neckHalf, shoulderBottom,
                rect.left, neckBottom,
                rect.left, shoulderBottom,
            )
            lineTo(rect.left, rect.bottom - baseCut)
            quadraticBezierTo(rect.left, rect.bottom, rect.left + baseCut, rect.bottom)
            lineTo(rect.right - baseCut, rect.bottom)
            quadraticBezierTo(rect.right, rect.bottom, rect.right, rect.bottom - baseCut)
            lineTo(rect.right, shoulderBottom)
            cubicTo(
                rect.right, neckBottom,
                centerX + neckHalf, shoulderBottom,
                centerX + neckHalf, neckBottom,
            )
            lineTo(centerX + neckHalf, rect.top)
            close()
        }
    }

    // ---------------------------------------------------------------- bottle

    /**
     * Draws one bottle: halo, glass, liquid, highlight, rim.
     *
     * @param segments liquid bottom -> top, already adjusted for any in-flight
     *   pour so the renderer never has to know about game rules.
     */
    fun drawBottle(
        scope: DrawScope,
        geometry: BoardGeometry,
        rect: CellRect,
        segments: List<Int>,
        capacity: Int,
        palette: LiquidPalette,
        style: BottleStyle,
        glassFill: Color,
        glassStroke: Color,
        shine: Color,
        liftPx: Float = 0f,
        tiltDegrees: Float = 0f,
        extraScale: Float = 1f,
        haloColor: Color = Color.Transparent,
        haloStrength: Float = 0f,
        offsetX: Float = 0f,
        rimColor: Color = Color.Transparent,
    ) {
        with(scope) {
            val pivot = Offset(rect.centerX, rect.bottom)
            translate(left = offsetX, top = -liftPx) {
                rotate(degrees = tiltDegrees, pivot = pivot) {
                    scale(scaleX = extraScale, scaleY = extraScale, pivot = pivot) {
                        if (haloStrength > 0f && haloColor != Color.Transparent) {
                            val haloRadius = rect.width * 1.15f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        haloColor.copy(alpha = 0.42f * haloStrength),
                                        haloColor.copy(alpha = 0f),
                                    ),
                                    center = Offset(rect.centerX, rect.centerY),
                                    radius = haloRadius,
                                ),
                                radius = haloRadius,
                                center = Offset(rect.centerX, rect.centerY),
                            )
                        }

                        val path = bottlePath(rect, style)
                        drawPath(path = path, color = glassFill)
                        val wallPx = wall(rect)

                        clipPath(path = path, clipOp = ClipOp.Intersect) {
                            for (index in segments.indices) {
                                val segmentRect = geometry.segmentRect(rect, wallPx, index, capacity)
                                val color = Color(palette.colorForId(segments[index]))
                                drawRect(
                                    color = color,
                                    topLeft = Offset(segmentRect.left, segmentRect.top),
                                    // +1px overlap removes hairline seams between segments.
                                    size = Size(segmentRect.width, segmentRect.height + 1f),
                                )
                                // Two faint bubbles per segment, deterministic.
                                val bubbleRadius = segmentRect.width * 0.055f
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.20f),
                                    radius = bubbleRadius,
                                    center = Offset(
                                        segmentRect.left + segmentRect.width * (0.26f + ((index * 37) % 23) / 100f),
                                        segmentRect.top + segmentRect.height * (0.32f + ((index * 53) % 31) / 100f),
                                    ),
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.13f),
                                    radius = bubbleRadius * 0.7f,
                                    center = Offset(
                                        segmentRect.left + segmentRect.width * (0.62f + ((index * 29) % 19) / 100f),
                                        segmentRect.top + segmentRect.height * (0.66f + ((index * 41) % 21) / 100f),
                                    ),
                                )
                            }

                            if (segments.isNotEmpty()) {
                                // Meniscus: a lighter band on the liquid surface.
                                val topRect =
                                    geometry.segmentRect(rect, wallPx, segments.size - 1, capacity)
                                val bandHeight = (topRect.height * 0.16f).coerceIn(2f, 7f)
                                drawRect(
                                    color = Color.White.copy(alpha = 0.30f),
                                    topLeft = Offset(topRect.left, topRect.top),
                                    size = Size(topRect.width, bandHeight),
                                )
                            }

                            // Glass highlight, drawn above the liquid.
                            drawRoundRect(
                                color = shine,
                                topLeft = Offset(
                                    rect.left + rect.width * 0.15f,
                                    rect.top + rect.height * 0.10f,
                                ),
                                size = Size(rect.width * 0.13f, rect.height * 0.58f),
                                cornerRadius = CornerRadius(rect.width * 0.07f, rect.width * 0.07f),
                            )
                        }

                        drawPath(
                            path = path,
                            color = glassStroke,
                            style = Stroke(
                                width = wallPx * 1.7f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )

                        if (rimColor != Color.Transparent) {
                            // Lip ring: gives the "simple geometric bottle" its top.
                            val rimHeight = (rect.width * 0.13f).coerceAtMost(rect.height * 0.05f)
                            val rimInset = rect.width * 0.06f
                            drawRoundRect(
                                color = rimColor,
                                topLeft = Offset(rect.left - rimInset, rect.top - rimHeight * 0.4f),
                                size = Size(rect.width + rimInset * 2, rimHeight),
                                cornerRadius = CornerRadius(rimHeight * 0.5f, rimHeight * 0.5f),
                                style = Stroke(width = rimHeight * 0.55f),
                            )
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ pour

    /**
     * Liquid stream between two bottles during a pour.
     *
     * @param fraction 0..1 position of the leading droplet along the arc.
     */
    fun drawPourStream(
        scope: DrawScope,
        geometry: BoardGeometry,
        from: CellRect,
        to: CellRect,
        color: Color,
        fraction: Float,
        targetFilled: Int,
        capacity: Int,
        tiltDegrees: Float,
    ) {
        with(scope) {
            val direction = if (to.centerX >= from.centerX) 1f else -1f
            // The lip moves with the tilted bottle.
            val radians = Math.toRadians(tiltDegrees.toDouble())
            val lipLocal = Offset(from.width * 0.42f * direction, -from.height * 0.48f)
            val lip = Offset(
                from.centerX + (lipLocal.x * cos(radians).toFloat() - lipLocal.y * sin(radians).toFloat()),
                from.bottom + (lipLocal.x * sin(radians).toFloat() + lipLocal.y * cos(radians).toFloat()),
            )
            val surface = Offset(
                to.centerX,
                geometry.surfaceY(to, wall(to), targetFilled, capacity) - to.height * 0.02f,
            )
            val control = Offset(
                (lip.x + surface.x) * 0.5f,
                minOf(lip.y, surface.y) - abs(surface.x - lip.x) * 0.22f - from.height * 0.10f,
            )

            val path = Path().apply {
                moveTo(lip.x, lip.y)
                quadraticBezierTo(control.x, control.y, surface.x, surface.y)
            }
            val thickness = (from.width * 0.20f).coerceAtLeast(4f)

            // Faint full arc shows where the liquid is travelling.
            drawPath(
                path = path,
                color = color.copy(alpha = 0.22f),
                style = Stroke(width = thickness, cap = StrokeCap.Round),
            )
            // Bright droplet riding the arc.
            val point = quadPoint(lip, control, surface, fraction.coerceIn(0f, 1f))
            drawCircle(color = color, radius = thickness * 0.72f, center = point)
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = thickness * 0.28f,
                center = Offset(point.x - thickness * 0.2f, point.y - thickness * 0.22f),
            )
        }
    }

    /** Point on a quadratic Bezier at [t]. */
    fun quadPoint(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
        val u = 1f - t
        return Offset(
            u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x,
            u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y,
        )
    }

    // ------------------------------------------------------------- celebrate

    /**
     * Confetti + glow for the level-complete animation. Fully deterministic per
     * particle index, so it costs no allocation and no random state.
     */
    fun drawCelebration(
        scope: DrawScope,
        width: Float,
        height: Float,
        palette: LiquidPalette,
        progress: Float,
        accent: Color,
    ) {
        if (progress <= 0f || progress >= 1f) return
        with(scope) {
            // Soft radial flash behind the board.
            val flash = sin((progress * Math.PI).toFloat()) * 0.16f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = flash), Color.Transparent),
                    center = Offset(width * 0.5f, height * 0.45f),
                    radius = width * 0.85f,
                ),
                size = Size(width, height),
            )

            val particles = 54
            for (index in 0 until particles) {
                val seedA = (index * 37) % 100 / 100f
                val seedB = (index * 53) % 100 / 100f
                val seedC = (index * 29) % 100 / 100f
                val fall = 0.72f + seedB * 0.5f
                val x = width * (0.04f + seedA * 0.92f) + sin(progress * 7f + index) * width * 0.03f
                val y = -height * 0.08f - seedC * height * 0.18f + progress * height * fall * 1.25f
                if (y > height * 1.05f) continue
                val size = (4f + seedC * 7f) * (width / 420f).coerceIn(0.6f, 1.6f)
                val color = Color(palette.color(index % palette.colors.size))
                val alpha = (1f - progress * 0.55f).coerceIn(0f, 1f)
                val rotation = progress * 360f * (0.4f + seedB)
                translate(left = x, top = y) {
                    rotate(degrees = rotation) {
                        if (index % 3 == 0) {
                            drawCircle(color = color.copy(alpha = alpha), radius = size * 0.5f)
                        } else {
                            drawRoundRect(
                                color = color.copy(alpha = alpha),
                                topLeft = Offset(-size * 0.5f, -size * 0.28f),
                                size = Size(size, size * 0.56f),
                                cornerRadius = CornerRadius(size * 0.28f, size * 0.28f),
                            )
                        }
                    }
                }
            }
        }
    }
}
