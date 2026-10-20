package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.compose.render.PolyColor

private val QUALITY_STOPS = listOf(
    PolyColor(0xFF1B7F3B.toInt()),
    PolyColor(0xFF55DD55.toInt()),
    PolyColor(0xFFFFD24A.toInt()),
    PolyColor(0xFFFF5555.toInt()),
)

fun qualityColor(quality: Float): PolyColor {
    val position = (1f - quality.coerceIn(0f, 1f)) * (QUALITY_STOPS.size - 1)
    val index = position.toInt().coerceAtMost(QUALITY_STOPS.size - 2)
    return QUALITY_STOPS[index].lerp(QUALITY_STOPS[index + 1], position - index)
}

fun quality(value: Float, worst: Float, best: Float): Float {
    if (worst == best) return if (value == best) 1f else 0f
    return ((value - worst) / (best - worst)).coerceIn(0f, 1f)
}
