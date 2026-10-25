// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.round

internal object GuiScaleMath {
    fun vanillaScale(width: Int, height: Int, choice: Int, unicode: Boolean): Int {
        val target = when (choice) {
            0 -> 1
            1 -> 2
            2 -> 3
            else -> 1000
        }
        var scale = 1
        while (scale < target && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) scale++
        if (unicode && scale > 1 && scale % 2 != 0) scale--
        return scale
    }

    fun multiplier(adjustment: Float): Double {
        val safe = if (adjustment.isFinite()) adjustment.coerceIn(-0.5f, 0.5f) else 0f
        return 2.0.pow(round(safe.toDouble() * 100.0) / 50.0)
    }

    fun logicalSize(physical: Int, scale: Double): Int = ceil(physical.coerceAtLeast(1) / scale).toInt().coerceAtLeast(1)

    fun mouseX(physicalX: Int, physicalWidth: Int, logicalWidth: Int): Int =
        ((physicalX.toLong() * logicalWidth) / physicalWidth.coerceAtLeast(1)).toInt()

    fun mouseY(physicalY: Int, physicalHeight: Int, logicalHeight: Int): Int =
        logicalHeight - ((physicalY.toLong() * logicalHeight) / physicalHeight.coerceAtLeast(1)).toInt() - 1
}
