// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GammaProfileStateTest {
    @Test void legacyToggleUsesConfiguredNativeValuesWithoutChangingTheChoice() {
        double normal = .75, full = 9.5;
        assertEquals(full, GammaProfileState.target(true, 15, normal, full));
        assertEquals(normal, GammaProfileState.target(false, 15, normal, full));
        assertEquals(normal, GammaProfileState.target(true, 0, normal, full));
        assertEquals(normal + (full - normal) * 8 / 15, GammaProfileState.target(true, 8, normal, full));
        assertEquals(9.5, full);
    }
    @Test void appliedMarkerDistinguishesLegacyEditsFromUnchangedNativeChoices() {
        var nativeState = JsonParser.parseString("{\"gamma\":{\"value\":7.25,\"toggledGamma\":950}}").getAsJsonObject();
        var file = JsonParser.parseString("{\"gamma\":{\"value\":1}}").getAsJsonObject();
        var state = GammaProfileState.remember(nativeState, file, true, 15);
        assertFalse(GammaProfileState.legacyChanged(state, true, 15));
        assertTrue(GammaProfileState.legacyChanged(state, false, 15));
        assertTrue(GammaProfileState.legacyChanged(state, true, 8));
        assertEquals(7.25, state.getAsJsonObject("gamma").get("value").getAsDouble());
        assertEquals(file, state.get(GammaProfileState.NATIVE_MARKER));
        assertFalse(nativeState.has(GammaProfileState.LEGACY_MARKER));
    }
    @Test void restoringNativeStateKeepsCachedManagerObjectReferences() throws Exception {
        var gson = new Gson();
        var target = new NativeConfig();
        var managerReference = target.gamma;
        var restored = gson.fromJson("{\"gamma\":{\"value\":7.25,\"toggledGamma\":950,\"dynamic\":true}}", NativeConfig.class);
        GammaProfileState.overwrite(target, restored);
        assertSame(managerReference, target.gamma);
        assertEquals(7.25, managerReference.value);
        assertEquals(950, managerReference.toggledGamma);
        assertTrue(managerReference.dynamic);
    }
    @Test void nativeSavePreservesUnknownNestedValuesAndTheOriginalSourceDocument() {
        var prior = JsonParser.parseString("{\"gamma\":{\"value\":1,\"future\":{\"kept\":true}},\"externalOnly\":17}").getAsJsonObject();
        var current = JsonParser.parseString("{\"gamma\":{\"value\":7.25,\"toggledGamma\":950}}").getAsJsonObject();
        JsonObject merged = GammaProfileState.merge(prior, current);
        assertEquals(7.25, merged.getAsJsonObject("gamma").get("value").getAsDouble());
        assertTrue(merged.getAsJsonObject("gamma").getAsJsonObject("future").get("kept").getAsBoolean());
        assertEquals(17, merged.get("externalOnly").getAsInt());
        assertEquals(1, prior.getAsJsonObject("gamma").get("value").getAsInt());
    }
    @Test void profilesWithoutNativeStateStartFromNativeBaselineRatherThanThePreviousProfile() throws Exception {
        var gson = new Gson();
        var baseline = JsonParser.parseString("{\"gamma\":{\"value\":1,\"toggledGamma\":950,\"dynamic\":false}}").getAsJsonObject();
        var external = JsonParser.parseString("{\"gamma\":{\"value\":2.5}}").getAsJsonObject();
        var previous = new NativeConfig();
        previous.gamma.value = 7.25;
        previous.gamma.toggledGamma = 1800;
        previous.gamma.dynamic = true;
        GammaProfileState.overwrite(previous, gson.fromJson(GammaProfileState.merge(baseline, external), NativeConfig.class));
        assertEquals(2.5, previous.gamma.value);
        assertEquals(950, previous.gamma.toggledGamma);
        assertFalse(previous.gamma.dynamic);
        assertEquals(1, baseline.getAsJsonObject("gamma").get("value").getAsInt());
    }
    private static final class NativeConfig { NativeGamma gamma = new NativeGamma(); }
    private static final class NativeGamma { double value = 1; int toggledGamma = 1500; boolean dynamic; }
}
