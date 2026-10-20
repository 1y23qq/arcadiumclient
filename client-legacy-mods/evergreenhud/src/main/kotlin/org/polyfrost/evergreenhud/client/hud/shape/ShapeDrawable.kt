package org.polyfrost.evergreenhud.client.hud.shape

import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Rect
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = -PI / 2.0

fun polygonPath(cx: Float, cy: Float, rx: Float, ry: Float, sides: Int): Path =
    buildPath(sides) { i ->
        val angle = START_ANGLE + i * 2.0 * PI / sides
        (cx + rx * cos(angle).toFloat()) to (cy + ry * sin(angle).toFloat())
    }

fun starPath(cx: Float, cy: Float, rx: Float, ry: Float, points: Int, innerRatio: Float): Path =
    buildPath(points * 2) { i ->
        val ratio = if (i % 2 == 0) 1f else innerRatio
        val angle = START_ANGLE + i * PI / points
        (cx + rx * ratio * cos(angle).toFloat()) to (cy + ry * ratio * sin(angle).toFloat())
    }

private inline fun buildPath(vertices: Int, vertex: (Int) -> Pair<Float, Float>): Path {
    val builder = PathBuilder()
    builder.use { builder ->
        for (i in 0 until vertices) {
            val (x, y) = vertex(i)
            if (i == 0) builder.moveTo(x, y) else builder.lineTo(x, y)
        }
        builder.closePath()
        return builder.detach()
    }
}

fun RenderContext.fillPath(path: Path, color: PolyColor) {
    if (color.alpha == 0) return
    paint.color = color.argb
    paint.mode = PaintMode.FILL
    canvas.drawPath(path, paint)
}

fun RenderContext.strokePath(path: Path, color: PolyColor, strokeWidth: Float) {
    if (color.alpha == 0) return
    paint.color = color.argb
    paint.mode = PaintMode.STROKE
    paint.strokeWidth = strokeWidth
    canvas.drawPath(path, paint)
    paint.mode = PaintMode.FILL
}

fun RenderContext.oval(x: Float, y: Float, w: Float, h: Float, color: PolyColor) {
    if (color.alpha == 0) return
    paint.color = color.argb
    paint.mode = PaintMode.FILL
    canvas.drawOval(Rect.makeXYWH(x, y, w, h), paint)
}

fun RenderContext.ovalStroke(x: Float, y: Float, w: Float, h: Float, color: PolyColor, strokeWidth: Float) {
    if (color.alpha == 0) return
    paint.color = color.argb
    paint.mode = PaintMode.STROKE
    paint.strokeWidth = strokeWidth
    canvas.drawOval(Rect.makeXYWH(x, y, w, h), paint)
    paint.mode = PaintMode.FILL
}

fun RenderContext.arcStroke(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    startAngle: Float,
    sweep: Float,
    color: PolyColor,
    thickness: Float,
) {
    if (color.alpha == 0) return
    val inset = thickness / 2f
    paint.color = color.argb
    paint.mode = PaintMode.STROKE
    paint.strokeWidth = thickness
    canvas.drawArc(x + inset, y + inset, x + w - inset, y + h - inset, startAngle - 90f, sweep, false, paint)
    paint.mode = PaintMode.FILL
}
