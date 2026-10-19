// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.features.BackgroundOpacity

class BackgroundOpacityTest {
    @Test
    fun `zero removes every background alpha while preserving its RGB`() {
        for (color in listOf(0x80000000.toInt(), 0xC0101010.toInt(), 0x20ABCDEF)) {
            assertEquals(color and 0xFFFFFF, BackgroundOpacity.withAlpha(color, 0f))
        }
    }

    @Test
    fun `opacity is absolute so a translucent background can become fully opaque`() {
        assertEquals(0x80ABCDEF.toInt(), BackgroundOpacity.withAlpha(0x20ABCDEF, 50f))
        assertEquals(0xFFABCDEF.toInt(), BackgroundOpacity.withAlpha(0x20ABCDEF, 100f))
    }

    @Test
    fun `out of range and nonfinite config values are bounded`() {
        assertEquals(0x00ABCDEF, BackgroundOpacity.withAlpha(0x20ABCDEF, -10f))
        assertEquals(0xFFABCDEF.toInt(), BackgroundOpacity.withAlpha(0x20ABCDEF, 1000f))
        assertEquals(0x80ABCDEF.toInt(), BackgroundOpacity.withAlpha(0x20ABCDEF, Float.NaN))
        assertEquals(0x80ABCDEF.toInt(), BackgroundOpacity.withAlpha(0x20ABCDEF, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `the default container dim retains the vanilla gradient and blur transition`() {
        val bottom = BackgroundOpacity.containerAlpha(81.56863f, true, 1f)
        val top = BackgroundOpacity.containerAlpha(81.56863f, false, 1f)
        assertEquals(0xD0101010.toInt(), BackgroundOpacity.withAlpha(0x00101010, bottom))
        assertEquals(0xC0101010.toInt(), BackgroundOpacity.withAlpha(0x00101010, top))
        assertEquals(bottom / 2, BackgroundOpacity.containerAlpha(81.56863f, true, 0.5f))
        assertEquals(0f, BackgroundOpacity.containerAlpha(0f, true, 1f))
        assertEquals(0f, BackgroundOpacity.containerAlpha(0f, false, 1f))
    }
}
