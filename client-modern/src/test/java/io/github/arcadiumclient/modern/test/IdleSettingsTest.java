// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.modern.client.IdleSettings;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class IdleSettingsTest {
    @TempDir Path directory;
    private JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    @Test void newUsersStayDisabledAndExistingEnableChoicesAreUntouched() throws Exception {
        IdleSettings.prepare(directory);
        Path file = directory.resolve("dynamic_fps.json");
        assertFalse(ModernSettingsMigration.read(file).get("enabled").getAsBoolean());
        Files.writeString(file, "{\"enabled\":true,\"extra\":1}");
        String before = Files.readString(file); IdleSettings.prepare(directory); assertEquals(before, Files.readString(file));
        Files.writeString(file, "{}"); IdleSettings.prepare(directory); assertEquals("{}", Files.readString(file));
    }
    @Test void namedProfilesKeepNestedUnknownFieldsWithoutRestoringRemovedVolumeEntries() throws Exception {
        Path root = directory.resolve("config/dynamic_fps.json"), profile = directory.resolve("profiles/Low/dynamic_fps.json");
        var defaults = json("{\"enabled\":true,\"idle\":{\"timeout\":300},\"states\":{\"unfocused\":{\"frame_rate_target\":1,\"volume_multipliers\":{\"master\":0.25}}}}");
        ModernSettingsMigration.write(root, defaults);
        var legacy = json("{\"enabled\":true,\"idle\":{\"timeout\":30,\"customOption\":\"keep\"},\"states\":{\"unfocused\":{\"frame_rate_target\":8,\"volume_multipliers\":{\"master\":0.1,\"music\":0.0}}},\"unknown\":42}");
        ModernSettingsMigration.write(profile, legacy);
        String rootBefore = Files.readString(root);
        var loaded = IdleSettings.load(profile, defaults); assertTrue(loaded.get("enabled").getAsBoolean());
        assertEquals(30, loaded.getAsJsonObject("idle").get("timeout").getAsInt());
        var encoded = json("{\"enabled\":false,\"idle\":{\"timeout\":60},\"states\":{\"unfocused\":{\"frame_rate_target\":5,\"volume_multipliers\":{\"master\":0.5}}}}");
        IdleSettings.save(profile, encoded);
        var saved = ModernSettingsMigration.read(profile);
        assertEquals("keep", saved.getAsJsonObject("idle").get("customOption").getAsString());
        assertEquals(42, saved.get("unknown").getAsInt());
        assertFalse(saved.getAsJsonObject("states").getAsJsonObject("unfocused").getAsJsonObject("volume_multipliers").has("music"));
        assertEquals(rootBefore, Files.readString(root));
    }
    @Test void missingNamedProfilesStayDisabledAndCorruptFilesArePreserved() throws Exception {
        var defaults = json("{\"enabled\":true,\"idle\":{\"timeout\":300}}");
        Path file = directory.resolve("profiles/Bad/dynamic_fps.json");
        assertFalse(IdleSettings.load(file, defaults).get("enabled").getAsBoolean());
        Files.createDirectories(file.getParent()); Files.writeString(file, "{broken");
        assertThrows(java.io.IOException.class, () -> IdleSettings.save(file, defaults));
        assertEquals("{broken", Files.readString(file));
    }
}
