// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.features.GuiScaleMath

class GuiScaleMathTest {
    @Test
    fun `the four choices match vanilla resolution constraints`() {
        assertEquals(listOf(1, 2, 3, 4), (0..3).map { GuiScaleMath.vanillaScale(1920, 1080, it, false) })
        assertEquals(listOf(1, 2, 2, 2), (0..3).map { GuiScaleMath.vanillaScale(854, 480, it, false) })
        assertEquals(listOf(1, 1, 1, 1), (0..3).map { GuiScaleMath.vanillaScale(640, 360, it, false) })
    }

    @Test
    fun `unicode keeps vanilla even-numbered scale behavior`() {
        assertEquals(listOf(1, 2, 2, 4), (0..3).map { GuiScaleMath.vanillaScale(1920, 1080, it, true) })
        assertEquals(2, GuiScaleMath.vanillaScale(1280, 720, 3, true))
    }

    @Test
    fun `slider endpoints halve or double both dimensions`() {
        assertEquals(0.5, GuiScaleMath.multiplier(-0.5f), 1e-9)
        assertEquals(1.0, GuiScaleMath.multiplier(0f), 1e-9)
        assertEquals(2.0, GuiScaleMath.multiplier(0.5f), 1e-9)
        assertEquals(1.013959479790029, GuiScaleMath.multiplier(0.01f), 1e-9)
    }

    @Test
    fun `persisted invalid slider values cannot corrupt the projection`() {
        assertEquals(1.0, GuiScaleMath.multiplier(Float.NaN))
        assertEquals(1.0, GuiScaleMath.multiplier(Float.POSITIVE_INFINITY))
        assertEquals(2.0, GuiScaleMath.multiplier(10f))
        assertEquals(0.5, GuiScaleMath.multiplier(-10f))
        assertEquals(1.0, GuiScaleMath.multiplier(0.004f))
    }

    @Test
    fun `mouse coordinates share the complete logical projection`() {
        val scale = 2.0 * GuiScaleMath.multiplier(0.23f)
        val width = GuiScaleMath.logicalSize(1919, scale)
        val height = GuiScaleMath.logicalSize(1079, scale)
        assertEquals(width - 1, GuiScaleMath.mouseX(1918, 1919, width))
        assertEquals(0, GuiScaleMath.mouseY(1078, 1079, height))
        assertEquals(0, GuiScaleMath.mouseX(0, 1919, width))
        assertEquals(height - 1, GuiScaleMath.mouseY(0, 1079, height))
        assertEquals(width / 2, GuiScaleMath.mouseX(960, 1919, width))
    }
}
