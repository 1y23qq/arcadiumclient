// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.JsonObject;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LegacyYaclStore {
    public record Plan(JsonObject desired, JsonObject marker, Path markerPath) {
        public void commit() throws IOException { writeChanged(markerPath, marker); }
    }
    private LegacyYaclStore() {}
    public static Plan load(LegacyYaclSchema.Spec spec, Path folder, String nativeFileName, JsonObject defaults) throws IOException {
        JsonObject raw = read(folder.resolve(spec.legacyFile()));
        JsonObject nativeFile = spec.legacyFile().equals(nativeFileName) ? new JsonObject() : read(folder.resolve(nativeFileName));
        Path markerPath = markerPath(folder, spec);
        JsonObject marker = read(markerPath);
        JsonObject desired = LegacyYaclSchema.toNative(spec, raw, nativeFile, marker, defaults);
        marker.addProperty("version", 1);
        marker.add("nativeFile", nativeFile);
        marker.add("legacy", raw);
        marker.add("nativeApplied", desired.deepCopy());
        return new Plan(desired, marker, markerPath);
    }
    public static void save(LegacyYaclSchema.Spec spec, Path folder, JsonObject encoded) throws IOException {
        save(spec, folder, encoded, new JsonObject());
    }
    public static void save(LegacyYaclSchema.Spec spec, Path folder, JsonObject encoded, JsonObject extra) throws IOException {
        Path file = folder.resolve(spec.legacyFile());
        JsonObject previous = read(file);
        extra.entrySet().forEach(entry -> previous.add(entry.getKey(), entry.getValue().deepCopy()));
        JsonObject marker = read(markerPath(folder, spec));
        JsonObject value = LegacyYaclSchema.toLegacy(spec, previous, encoded);
        writeChanged(file, value);
        marker.addProperty("version", 1);
        marker.add("legacy", value.deepCopy());
        marker.add("nativeApplied", encoded.deepCopy());
        writeChanged(markerPath(folder, spec), marker);
    }
    private static Path markerPath(Path folder, LegacyYaclSchema.Spec spec) { return folder.resolve("arcadium/modern-native-profiles").resolve(spec.modId() + ".json"); }
    private static JsonObject read(Path file) throws IOException { return Files.exists(file) ? ModernSettingsMigration.read(file) : new JsonObject(); }
    private static void writeChanged(Path file, JsonObject value) throws IOException { if (!Files.exists(file) || !ModernSettingsMigration.read(file).equals(value)) ModernSettingsMigration.write(file, value); }
}
