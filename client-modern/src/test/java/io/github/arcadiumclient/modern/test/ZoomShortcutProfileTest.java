// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import io.github.arcadiumclient.modern.client.ZoomShortcutProfile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZoomShortcutProfileTest {
    private JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    @Test void legacyProfileImportsOnlyWhenModernControlsHaveNoExplicitPreference() {
        var old = json("{\"zoomKey\":{\"keyCodes\":[\"key.keyboard.v\"]}}");
        assertEquals("key.keyboard.v", ZoomShortcutProfile.imports(old, new JsonObject(), new JsonObject(), new JsonObject()).get("zoomKey").getAsString());
        assertTrue(ZoomShortcutProfile.imports(old, json("{\"zoomify.key.zoom\":\"key.keyboard.r\"}"), new JsonObject(), new JsonObject()).isEmpty());
    }
    @Test void laterLegacyChangesShareKeysButSimultaneousModernChangesWin() {
        var old = json("{\"zoomKey\":{\"keyCodes\":[\"key.keyboard.c\"]}}");
        var tracked = new JsonObject();
        var pair = new JsonObject(); pair.addProperty("legacy", ModernSettingsMigration.fingerprint(old.getAsJsonObject("zoomKey"))); pair.addProperty("native", "key.keyboard.r"); tracked.add("zoomKey", pair);
        old.getAsJsonObject("zoomKey").add("keyCodes", JsonParser.parseString("[\"key.keyboard.v\"]"));
        assertEquals("key.keyboard.v", ZoomShortcutProfile.imports(old, json("{\"zoomify.key.zoom\":\"key.keyboard.r\"}"), new JsonObject(), tracked).get("zoomKey").getAsString());
        assertTrue(ZoomShortcutProfile.imports(old, json("{\"zoomify.key.zoom\":\"key.keyboard.u\"}"), new JsonObject(), tracked).isEmpty());
    }
    @Test void importedCompoundBindingSurvivesItsNativeUnboundMirror() {
        var old = json("{\"zoomKey\":{\"keyCodes\":[\"key.keyboard.z\"],\"mods\":2,\"customOption\":{\"a\":1}}}");
        var controls = json("{\"zoomify.key.zoom\":\"key.keyboard.unknown\"}");
        assertTrue(ZoomShortcutProfile.imports(old, controls, json("{\"zoomKey\":true}"), new JsonObject()).get("zoomKey").isJsonNull());
        assertTrue(ZoomShortcutProfile.imports(old, controls, new JsonObject(), new JsonObject()).isEmpty());
        var binding = old.getAsJsonObject("zoomKey");
        ModernSettingsMigration.applyNativeKey(binding, "key.mouse.right");
        assertEquals(1, binding.getAsJsonObject("customOption").get("a").getAsInt());
        assertFalse(binding.has("mods"));
        assertEquals("key.mouse.right", binding.getAsJsonArray("mouseBtns").get(0).getAsString());
    }
}
