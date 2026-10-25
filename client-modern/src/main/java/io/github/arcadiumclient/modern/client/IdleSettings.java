// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IdleSettings {
    private IdleSettings() {}
    public static void prepare(Path config) throws IOException {
        Path file = config.resolve("dynamic_fps.json");
        if (Files.exists(file)) return;
        JsonObject value = new JsonObject(); value.addProperty("enabled", false);
        ModernSettingsMigration.write(file, value);
    }
    public static JsonObject load(Path file, JsonObject defaults) throws IOException {
        JsonObject base = defaults.deepCopy(); base.addProperty("enabled", false);
        return Files.exists(file) ? merge(base, ModernSettingsMigration.read(file)) : base;
    }
    public static void save(Path file, JsonObject encoded) throws IOException {
        JsonObject previous = Files.exists(file) ? ModernSettingsMigration.read(file) : new JsonObject();
        JsonObject value = merge(previous, encoded);
        if (!value.equals(previous)) ModernSettingsMigration.write(file, value);
    }
    public static JsonObject merge(JsonObject base, JsonObject updates) {
        JsonObject merged = base.deepCopy();
        for (var field : updates.entrySet()) {
            var prior = merged.get(field.getKey());
            if (!field.getKey().equals("volume_multipliers") && prior != null && prior.isJsonObject() && field.getValue().isJsonObject())
                merged.add(field.getKey(), merge(prior.getAsJsonObject(), field.getValue().getAsJsonObject()));
            else merged.add(field.getKey(), field.getValue().deepCopy());
        }
        return merged;
    }
}
