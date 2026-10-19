// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test;

import org.junit.jupiter.api.Test;
import io.github.arcadiumclient.runtime.client.features.HudValuePalette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HudValuePaletteTest {
    @Test
    void tabPingBoundariesIncludeZeroAndUnknownIsDistinct() {
        long[] readings = {-1, 0, 74, 75, 144, 145, 199, 200, 299, 300, 399, 400, Long.MAX_VALUE};
        int[] ranges = {-1, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5};
        for (int i = 0; i < readings.length; i++) assertEquals(ranges[i], HudValuePalette.pingRange(readings[i]));
    }

    @Test
    void tpsClassificationMatchesTheRenderedDecimalWithoutGaps() {
        float[] readings = {15f, 15.049f, 15.05f, 19.449f, 19.45f, 19.5f, 20.5f, 20.549f, 20.55f, 20.6f, 45f};
        int[] ranges = {2, 2, 1, 1, 0, 0, 0, 0, 2, 2, 2};
        String[] text = {"15.0", "15.0", "15.1", "19.4", "19.5", "19.5", "20.5", "20.5", "20.6", "20.6", "45.0"};
        for (int i = 0; i < readings.length; i++) {
            assertEquals(ranges[i], HudValuePalette.tpsRange(readings[i]));
            assertEquals(text[i], HudValuePalette.tpsText(readings[i]));
        }
        assertEquals(2, HudValuePalette.tpsRange(Float.NaN));
        assertEquals("Unknown", HudValuePalette.tpsText(Float.POSITIVE_INFINITY));
    }

    @Test
    void darkBiomeColorsAreReadableAndLightVanillaColorsStayExact() {
        assertEquals(0xFFFFFFFF, HudValuePalette.readableBiomeColor(0xFFFFFF));
        assertEquals(0xFFFFFF00, HudValuePalette.readableBiomeColor(0xFFFF00));
        assertNotEquals(0xFF000070, HudValuePalette.readableBiomeColor(0x000070));
        assertEquals(0xFF, HudValuePalette.readableBiomeColor(0) >>> 24);
    }
}
