// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import io.github.arcadiumclient.modern.client.features.HurtCameraMath;
import io.github.arcadiumclient.modern.client.features.VignettePolicy;
import io.github.arcadiumclient.modern.client.features.UnfocusedFpsPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VisualTweakPolicyTest {
    @Test void unfocusedCapPreservesDisabledAndNativeIdlePolicies() {
        assertEquals(10, UnfocusedFpsPolicy.cap(10, false, 120, false));
        assertEquals(120, UnfocusedFpsPolicy.cap(120, true, 5, true));
        assertEquals(10, UnfocusedFpsPolicy.cap(10, true, 200, false));
        assertEquals(5, UnfocusedFpsPolicy.cap(30, true, 5, false));
        assertEquals(5, UnfocusedFpsPolicy.cap(10, true, 5, false));
        assertEquals(60, UnfocusedFpsPolicy.cap(120, true, 60, false));
        assertEquals(60, UnfocusedFpsPolicy.cap(60, true, 120, false));
    }
    @Test void hurtCameraPreservesAxisAndNegativeMultiplierSemantics() {
        assertEquals(0, HurtCameraMath.angle(90, 0.3, true, true));
        assertEquals(27, HurtCameraMath.angle(90, 0.3, true, false), 0.0001);
        assertEquals(-4.2, HurtCameraMath.angle(-14, 0.3, false, true), 0.0001);
        assertEquals(7, HurtCameraMath.angle(-14, -0.5, false, false), 0.0001);
        assertTrue(Float.isFinite(HurtCameraMath.angle(180, Double.MAX_VALUE, false, false)));
    }
    @Test void vignetteHasRealStaticAndBoundedAmplifiedBehavior() {
        assertEquals(0.5, VignettePolicy.strength(0, 0.1f, 50, 0, 100, 1), 0.0001);
        assertEquals(0.6, VignettePolicy.strength(1, 0.3f, 50, 0, 100, 2), 0.0001);
        assertEquals(0.4, VignettePolicy.strength(1, 0.1f, 50, 40, 80, 1), 0.0001);
        assertEquals(0.8, VignettePolicy.strength(1, 1, 50, 40, 80, 1), 0.0001);
        assertEquals(0, VignettePolicy.strength(1, Float.NaN, 50, 0, 100, Float.NaN));
    }
    @Test void statusVignetteScalesAtThresholdAndKeepsNativeAlpha() {
        assertEquals(0, VignettePolicy.condition(80, 50, 0, 100));
        assertEquals(0, VignettePolicy.condition(50, 50, 0, 100));
        assertEquals(0.5, VignettePolicy.condition(25, 50, 0, 100), 0.0001);
        assertEquals(1, VignettePolicy.condition(0, 0, 0, 100));
        assertEquals(0xC0202020, VignettePolicy.tint(0xC0202020, 0xFFFF0000, 0));
        assertEquals(0xC000FFFF, VignettePolicy.tint(0xC0202020, 0xFFFF0000, 1));
    }
}
