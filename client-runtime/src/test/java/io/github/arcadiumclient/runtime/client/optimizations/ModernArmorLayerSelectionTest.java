// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ModernArmorLayerSelectionTest {
    @Test void baseAndGlintOwnOnlyTheTwoSelectedModels() {
        var selection = new ModernArmorLayerSelection();
        Object capture = new Object(), layer = new Object(), entity = new Object(), first = new Object(), second = new Object();
        selection.select(capture, layer, entity, first, second, 12);
        assertSame(capture, selection.capture());
        assertTrue(selection.model(first, entity, 12));
        assertTrue(selection.model(second, entity, 12));
        assertTrue(selection.layer(layer, first, entity, 12));
        assertFalse(selection.model(new Object(), entity, 12));
        assertFalse(selection.layer(new Object(), first, entity, 12));
    }

    @Test void identityFrameAndInvalidationKeepLaterLayersNative() {
        var selection = new ModernArmorLayerSelection();
        Object capture = new Object(), layer = new Object(), entity = new String("player"), model = new Object();
        selection.select(capture, layer, entity, model, model, 31);
        assertFalse(selection.model(model, new String("player"), 31));
        assertFalse(selection.model(model, entity, 32));
        assertFalse(selection.model(null, entity, 31));
        selection.clear();
        assertNull(selection.capture());
        assertFalse(selection.layer(layer, model, entity, 31));
    }

    @Test void replacementCannotReuseThePreviousPlayerOrModel() {
        var selection = new ModernArmorLayerSelection();
        Object first = new Object(), second = new Object(), player = new Object(), other = new Object();
        selection.select(new Object(), new Object(), player, first, first, 2);
        selection.select(new Object(), new Object(), other, second, second, 2);
        assertFalse(selection.model(first, player, 2));
        assertFalse(selection.model(first, other, 2));
        assertTrue(selection.model(second, other, 2));
    }

    @Test void originalMeshSelectionCannotEscapeTheNativeCaptureOrItsGeneration() {
        var selection = new ModernArmorLayerSelection();
        Object capture = new Object(), model = new Object();
        selection.select(capture, new Object(), new Object(), model, model, 15);
        assertTrue(selection.captureModel(capture, model, 15));
        assertFalse(selection.captureModel(new Object(), model, 15));
        assertFalse(selection.captureModel(capture, model, 16));
        selection.clear();
        assertFalse(selection.captureModel(capture, model, 15));
    }
}
