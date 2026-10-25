// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import com.google.gson.JsonObject;
import java.lang.reflect.Modifier;

public final class GammaProfileState {
    public static final String LEGACY_MARKER = "_arcadiumLegacy";
    public static final String NATIVE_MARKER = "_arcadiumExternalFile";
    private GammaProfileState() { }
    public static double target(boolean enabled, int level, double normal, double full) {
        return enabled ? normal + (full - normal) * Math.clamp(level, 0, 15) / 15.0 : normal;
    }
    public static boolean legacyChanged(JsonObject state, boolean enabled, int level) {
        if (!state.has(LEGACY_MARKER) || !state.get(LEGACY_MARKER).isJsonObject()) return true;
        var marker = state.getAsJsonObject(LEGACY_MARKER);
        return !marker.has("enable") || !marker.has("lightLevel") || marker.get("enable").getAsBoolean() != enabled || marker.get("lightLevel").getAsInt() != level;
    }
    public static JsonObject remember(JsonObject state, JsonObject external, boolean enabled, int level) {
        var result = state.deepCopy();
        var marker = new JsonObject();
        marker.addProperty("enable", enabled);
        marker.addProperty("lightLevel", level);
        result.add(LEGACY_MARKER, marker);
        result.add(NATIVE_MARKER, external.deepCopy());
        return result;
    }
    public static JsonObject merge(JsonObject prior, JsonObject current) {
        JsonObject result = prior.deepCopy();
        for (var entry : current.entrySet()) {
            if (entry.getValue().isJsonObject() && result.has(entry.getKey()) && result.get(entry.getKey()).isJsonObject())
                result.add(entry.getKey(), merge(result.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject()));
            else result.add(entry.getKey(), entry.getValue().deepCopy());
        }
        return result;
    }
    public static void overwrite(Object target, Object source) throws ReflectiveOperationException {
        for (var field : source.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()) || field.isSynthetic()) continue;
            field.setAccessible(true);
            Object old = field.get(target), value = field.get(source);
            if (old != null && value != null && !field.getType().isPrimitive() && !field.getType().isEnum() && !field.getType().getName().startsWith("java.")) overwrite(old, value);
            else field.set(target, value);
        }
    }
}
