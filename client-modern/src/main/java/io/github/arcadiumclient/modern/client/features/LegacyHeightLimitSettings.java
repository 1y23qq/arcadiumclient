// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class LegacyHeightLimitSettings {
    private static final String KEY = "minemenHeightLimit";
    private LegacyHeightLimitSettings() {}
    public static Boolean read(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        JsonObject object = parse(file);
        var value = object.get(KEY);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
    }
    public static void write(Path file, boolean enabled) throws IOException {
        JsonObject object = Files.exists(file) ? parse(file) : new JsonObject();
        if (object.has(KEY) && object.get(KEY).isJsonPrimitive() && object.get(KEY).getAsJsonPrimitive().isBoolean() && object.get(KEY).getAsBoolean() == enabled) return;
        object.addProperty(KEY, enabled);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".arcadium-height-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(object));
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static JsonObject parse(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 4 * 1024 * 1024) throw new IOException("Invalid legacy height settings.");
        try {
            var root = JsonParser.parseString(Files.readString(file));
            if (!root.isJsonObject()) throw new IOException("Invalid legacy height settings.");
            return root.getAsJsonObject();
        } catch (RuntimeException invalid) { throw new IOException("Could not read legacy height settings.", invalid); }
    }
}
