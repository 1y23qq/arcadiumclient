// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public final class ZoomShortcutProfile {
    private ZoomShortcutProfile() {}
    public static JsonObject imports(JsonObject legacy, JsonObject nativeControls, JsonObject previouslyBridged, JsonObject tracking) {
        JsonObject result = new JsonObject();
        for (var entry : ModernSettingsMigration.ZOOM_KEYS.entrySet()) {
            if (!legacy.has(entry.getKey()) || !legacy.get(entry.getKey()).isJsonObject()) continue;
            var saved = nativeControls.get(entry.getValue());
            var previous = tracking.getAsJsonObject(entry.getKey());
            boolean legacyChanged = previous != null && previous.has("legacy") && !previous.get("legacy").getAsString().equals(ModernSettingsMigration.fingerprint(legacy.getAsJsonObject(entry.getKey())));
            boolean nativeUnchanged = previous != null && previous.has("native") && saved != null && saved.equals(previous.get("native"));
            if (saved != null && !(legacyChanged && nativeUnchanged) && (!saved.isJsonPrimitive() || !saved.getAsString().equals("key.keyboard.unknown") || !previouslyBridged.has(entry.getKey()))) continue;
            String input = ModernSettingsMigration.nativeKey(legacy.getAsJsonObject(entry.getKey()));
            if (input == null) result.add(entry.getKey(), JsonNull.INSTANCE);
            else result.addProperty(entry.getKey(), input);
        }
        return result;
    }
}
