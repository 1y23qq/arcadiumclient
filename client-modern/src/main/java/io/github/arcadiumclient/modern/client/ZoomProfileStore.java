// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZoomProfileStore {
    private ZoomProfileStore() {}
    public static JsonObject load(Path file, JsonObject defaults) throws IOException {
        JsonObject source = Files.exists(file) ? ModernSettingsMigration.read(file) : new JsonObject();
        JsonObject value = defaults.deepCopy();
        for (var field : source.entrySet()) value.add(field.getKey(), field.getValue().deepCopy());
        if (!source.has("_keybindScrolling") && source.has("keybindScrolling")) value.add("_keybindScrolling", source.get("keybindScrolling").deepCopy());
        value.addProperty("_firstLaunch", false);
        return value;
    }
    public static void save(Path file, JsonObject encoded) throws IOException {
        JsonObject previous = Files.exists(file) ? ModernSettingsMigration.read(file) : new JsonObject();
        JsonObject merged = previous.deepCopy();
        for (var field : encoded.entrySet()) merged.add(field.getKey(), field.getValue().deepCopy());
        if (merged.has("_keybindScrolling")) merged.add("keybindScrolling", merged.get("_keybindScrolling").deepCopy());
        merged.addProperty("_firstLaunch", false);
        if (!merged.equals(previous)) ModernSettingsMigration.write(file, merged);
    }
}
