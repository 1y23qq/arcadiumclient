// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.ui.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background as flatBackground
import androidx.compose.foundation.border as flatBorder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

private class AccentFrameBrush(
    private val settings: ThemeAccent.Snapshot,
    private val phase: Float,
    private val alpha: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val width = (settings.smoothness() / 100f).coerceAtLeast(.02f)
        val start = -phase * size.width * 2
        val first = Color(settings.start()).copy(alpha = alpha)
        val second = Color(settings.end()).copy(alpha = alpha)
        return LinearGradientShader(
            from = Offset(start, 0f), to = Offset(start + size.width.coerceAtLeast(1f), 0f),
            colors = listOf(first, first, second, second),
            colorStops = listOf(0f, .5f - width / 2, .5f + width / 2, 1f), tileMode = TileMode.Mirror,
        )
    }
}

fun accentBrush(alpha: Float = 1f): Brush {
    val settings = accentSettings()
    return if (settings.gradient()) AccentFrameBrush(settings, accentPhase(), alpha.coerceIn(0f, 1f))
        else SolidColor(Accent.copy(alpha = alpha.coerceIn(0f, 1f)))
}

fun isAccentColor(color: Color): Boolean = color != Color.Unspecified && accentSettings().gradient() &&
    color.copy(alpha = 1f).toArgb() == Accent.copy(alpha = 1f).toArgb()

fun Modifier.accentAwareBackground(color: Color, shape: Shape = RectangleShape): Modifier =
    if (isAccentColor(color)) accentBackground(shape, color.alpha) else flatBackground(color, shape)

fun Modifier.accentAwareBackground(brush: Brush, shape: Shape = RectangleShape, alpha: Float = 1f): Modifier =
    flatBackground(brush, shape, alpha)

fun Modifier.accentAwareBorder(width: Dp, color: Color, shape: Shape = RectangleShape): Modifier =
    if (isAccentColor(color)) accentBorder(width, shape, color.alpha) else flatBorder(width, color, shape)

fun Modifier.accentAwareBorder(width: Dp, brush: Brush, shape: Shape = RectangleShape): Modifier =
    accentAwareBorder(BorderStroke(width, brush), shape)

fun Modifier.accentAwareBorder(border: BorderStroke, shape: Shape = RectangleShape): Modifier {
    val color = (border.brush as? SolidColor)?.value
    return if (color != null && isAccentColor(color)) accentBorder(border.width, shape, color.alpha) else flatBorder(border, shape)
}

fun Modifier.accentBorder(width: Dp, shape: Shape = RectangleShape, alpha: Float = 1f): Modifier = drawWithCache {
    val pixels = if (width == Dp.Hairline) 1f else width.toPx()
    val inside = Size((size.width - pixels).coerceAtLeast(0f), (size.height - pixels).coerceAtLeast(0f))
    val outline = shape.createOutline(inside, layoutDirection, this)
    onDrawWithContent {
        drawContent()
        if (pixels > 0f && alpha > 0f) inset(pixels / 2, pixels / 2) {
            drawOutline(outline, accentBrush(alpha), style = Stroke(pixels))
        }
    }
}

fun Modifier.accentTint(color: Color): Modifier = accentTint(isAccentColor(color))

fun Modifier.accentTint(enabled: Boolean): Modifier = if (!enabled) this else
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(accentBrush(), blendMode = BlendMode.SrcIn)
    }

fun DrawScope.drawAccentMask(mask: DrawScope.() -> Unit) {
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    try {
        mask()
        drawRect(accentBrush(), blendMode = BlendMode.SrcIn)
    } finally {
        canvas.restore()
    }
}
