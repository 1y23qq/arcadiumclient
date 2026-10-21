// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class MinecraftVersionNormalizer {
    private static final JsonObject TABLE = load();
    private static JsonObject load() {
        try (var input = MinecraftVersionNormalizer.class.getResourceAsStream("/arcadium-minecraft-versions.json")) {
            if (input == null) throw new IllegalStateException("Minecraft version boundaries are missing.");
            return new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    public static String getRelease(String version) {
        JsonObject record = TABLE.getAsJsonObject(version);
        if (record != null) return record.has("release") ? record.get("release").getAsString() : null;
        return version;
    }
    public static String normalizeVersion(String version, String release) {
        JsonObject record = TABLE.getAsJsonObject(version);
        if (record != null) return record.get("normalized").getAsString();
        return version.replaceFirst("^([0-9]+\\.[0-9]+)(?=-|$)", "$1.0");
    }
}
