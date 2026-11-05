// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativeMaterialStateTest {
    @Test void onlyTheActualBaseMaterialDecisionCarriesIntoItsGlint() {
        var state = new NativeMaterialState(); Object model = new Object(), ordinary = new Object(), glow = new Object();
        assertTrue(state.requires(model, ordinary, false, true, false, 1));
        assertFalse(state.requires(model, ordinary, true, false, false, 1));
        assertFalse(state.requires(model, new Object(), false, true, true, 1));
        assertTrue(state.requires(model, glow, true, false, true, 1));
        assertTrue(state.requires(model, new Object(), false, true, false, 1));
    }

    @Test void theNextOrdinaryArmorSlotDoesNotInheritAnEmissiveFallback() {
        var state = new NativeMaterialState(); Object sharedArmorModel = new Object();
        assertTrue(state.requires(sharedArmorModel, new Object(), true, false, true, 7));
        assertFalse(state.requires(sharedArmorModel, new Object(), true, false, false, 7));
        assertFalse(state.requires(sharedArmorModel, new Object(), false, true, false, 7));
    }

    @Test void anotherModelLayerEntityOrResourceGenerationCannotUseEarlierAdmission() {
        var state = new NativeMaterialState(); Object model = new Object(), texture = new Object();
        assertFalse(state.requires(model, texture, true, false, false, 1));
        assertTrue(state.requires(new Object(), texture, false, true, false, 1));
        assertTrue(state.requires(model, texture, false, true, false, 2));
        assertFalse(state.requires(model, texture, true, false, false, 2)); state.clear();
        assertTrue(state.requires(model, texture, false, true, false, 2));
    }

    @Test void missingMaterialAndUnknownPassStayOnTheNativePath() {
        var state = new NativeMaterialState(); Object model = new Object(), texture = new Object();
        assertTrue(state.requires(model, null, true, false, false, 1));
        assertTrue(state.requires(model, texture, false, true, false, 1));
        assertTrue(state.requires(null, texture, true, false, false, 1));
        assertTrue(state.requires(model, texture, false, false, false, 1));
    }
}
