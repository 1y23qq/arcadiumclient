// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LegacyCrosshairPolicyTest {
    @Test void preservesIndependentVisibilityConditions() {
        assertTrue(LegacyCrosshairPolicy.hidden(true, false, false, false, true));
        assertTrue(LegacyCrosshairPolicy.hidden(false, true, true, false, true));
        assertFalse(LegacyCrosshairPolicy.hidden(false, true, false, false, true));
        assertTrue(LegacyCrosshairPolicy.hidden(false, false, false, true, false));
        assertFalse(LegacyCrosshairPolicy.hidden(false, false, false, true, true));
    }
    @Test void translatesPercentageOpacityWithoutChangingZeroOrFullOpacity() {
        assertEquals(0, LegacyCrosshairPolicy.alpha(0));
        assertEquals(128, LegacyCrosshairPolicy.alpha(50));
        assertEquals(255, LegacyCrosshairPolicy.alpha(100));
        assertEquals(255, LegacyCrosshairPolicy.alpha(Float.NaN));
    }
    @Test void invertedBlendingUsesRgbWeightWhileNormalBlendingUsesAlpha() {
        assertEquals(0x80FFFFFF, LegacyCrosshairPolicy.tint(128, true));
        assertEquals(0xFF808080, LegacyCrosshairPolicy.tint(128, false));
        assertEquals(0xFF000000, LegacyCrosshairPolicy.tint(0, false));
        assertEquals(0xFFFFFFFF, LegacyCrosshairPolicy.tint(255, false));
    }
}
