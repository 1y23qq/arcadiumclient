// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.runtime.legacy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LegacyVisualMathTest {
    @Test void percentageHundredRetainsVanillaFlightSprintSpeedAndBowEffects() {
        assertEquals(1f, fov(.1, false, false, -1, 100, 100, 100, 100), .000001f);
        assertEquals(1.1f, fov(.1, false, true, -1, 100, 100, 100, 100), .000001f);
        assertEquals(1.15f, fov(.13, true, false, -1, 100, 100, 100, 100), .000001f);
        assertEquals(1.5f, fov(.2, false, false, -1, 100, 100, 100, 100), .000001f);
        assertEquals(.85f, fov(.1, false, false, 20, 100, 100, 100, 100), .000001f);
    }

    @Test void importedPercentagesControlEachEffectRatherThanAbsoluteCameraDegrees() {
        assertEquals(1.04f, fov(.1, false, true, -1, 100, 100, 40, 100), .000001f);
        assertEquals(1.09375f, fov(.13, true, false, -1, 100, 62.5f, 100, 100), .000001f);
        assertEquals(1.1875f, fov(.2, false, false, -1, 37.5f, 100, 100, 100), .000001f);
        assertEquals(.9625f, fov(.1, false, false, 20, 100, 100, 100, 25), .000001f);
        assertEquals(1f, fov(.26, true, true, 20, 0, 0, 0, 0), .000001f);
    }

    @Test void zeroWalkSpeedAndNonfiniteInputsKeepTheCameraFinite() {
        assertEquals(1f, LegacyVisualMath.movementFov(0, 0, true, true, -1, 100, 100, 100, 100));
        assertEquals(.85f, LegacyVisualMath.movementFov(Double.NaN, .1f, false, false, 20, 100, 100, 100, 100), .000001f);
        assertTrue(Float.isFinite(fov(.1, false, true, 20, Float.NaN, 100, Float.POSITIVE_INFINITY, 100)));
    }

    @Test void submergedEffectUsesTheSamePercentageScale() {
        assertEquals(60f, LegacyVisualMath.submergedFov(60f, 100f));
        assertEquals(70f, LegacyVisualMath.submergedFov(60f, 0f));
        assertEquals(68.5f, LegacyVisualMath.submergedFov(60f, 15f));
    }

    @Test void lightLevelReadsTheCorrespondingVanillaLightmapSampleAndPreservesDisabledPixels() {
        int[] original = new int[256];
        for (int i = 0; i < original.length; i++) original[i] = 0xFF000000 | i * 0x010101;
        int[] pixels = original.clone();
        LegacyVisualMath.applyLightLevel(pixels, false, 7);
        assertArrayEquals(original, pixels);
        LegacyVisualMath.applyLightLevel(pixels, true, 7);
        assertTrue(Arrays.stream(pixels).allMatch(color -> color == original[7 * 17]));
        pixels = original.clone(); LegacyVisualMath.applyLightLevel(pixels, true, 15);
        assertEquals(original[255], pixels[0]);
        pixels = original.clone(); LegacyVisualMath.applyLightLevel(pixels, true, -20);
        assertEquals(original[0], pixels[255]);
    }

    private static float fov(double speed, boolean sprint, boolean fly, int bow, float speedPct, float sprintPct, float flyPct, float bowPct) {
        return LegacyVisualMath.movementFov(speed, .1f, sprint, fly, bow, speedPct, sprintPct, flyPct, bowPct);
    }
}
