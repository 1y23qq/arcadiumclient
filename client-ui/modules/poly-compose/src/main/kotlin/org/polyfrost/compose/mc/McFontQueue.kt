package org.polyfrost.compose.mc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.skia.Canvas

object McFontQueue {
    data class Line(val text: String, val width: Float)

    var revision by mutableIntStateOf(0)
    var overrideModFont: (() -> Boolean)? = null

    var measureWidth: ((text: String, scale: Float) -> Float)? = null
    var measureHeight: ((scale: Float) -> Float)? = null

    var renderer: ((canvas: Canvas, text: String, x: Float, y: Float, color: Int, shadow: Boolean, scale: Float) -> Unit)? = null

    fun measureTextWidth(text: String, scale: Float): Float =
        measureWidth?.invoke(text, scale) ?: (text.length * 6f * scale)

    fun measureTextHeight(scale: Float): Float =
        measureHeight?.invoke(scale) ?: (9f * scale)

    fun layoutLines(text: String, scale: Float): List<Line> {
        val style = MinecraftTextStyle(-1)
        return text.lines().map { line ->
            val formatted = style.formatPrefix() + line
            var index = 0
            while (index + 1 < line.length) {
                if (line[index] == '§') { style.apply(line[index + 1]); index += 2 }
                else index++
            }
            Line(formatted, measureTextWidth(formatted, scale))
        }
    }

    fun lineOffset(width: Float, lineWidth: Float, align: Int): Float =
        (width - lineWidth).coerceAtLeast(0f) * (align.coerceIn(0, 2) * 0.5f)
}
