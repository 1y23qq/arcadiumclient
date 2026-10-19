package org.polyfrost.compose.mc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.skia.Canvas

object McFontQueue {
    var revision by mutableIntStateOf(0)
    var overrideModFont: (() -> Boolean)? = null

    var measureWidth: ((text: String, scale: Float) -> Float)? = null
    var measureHeight: ((scale: Float) -> Float)? = null

    var renderer: ((canvas: Canvas, text: String, x: Float, y: Float, color: Int, shadow: Boolean, scale: Float) -> Unit)? = null

    fun measureTextWidth(text: String, scale: Float): Float =
        measureWidth?.invoke(text, scale) ?: (text.length * 6f * scale)

    fun measureTextHeight(scale: Float): Float =
        measureHeight?.invoke(scale) ?: (9f * scale)
}
