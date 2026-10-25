// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import io.github.arcadiumclient.modern.client.ZoomProfileStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ZoomProfileStoreTest {
    @TempDir Path directory;
    private JsonObject fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/legacy-settings/" + name)) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void oldNamedProfileLoadsUsingTheModernCodecFieldsWithoutChangingItsSource() throws Exception {
        Path file = directory.resolve("profiles/Legacy/zoomify.json");
        var old = fixture("zoomify.json"); old.addProperty("initialZoom", 11); old.addProperty("customLegacyOption", "keep");
        ModernSettingsMigration.write(file, old);
        String before = Files.readString(file);
        var loaded = ZoomProfileStore.load(file, fixture("modern-zoomify.json"));
        assertEquals(11, loaded.get("initialZoom").getAsInt());
        assertEquals(old.get("keybindScrolling"), loaded.get("_keybindScrolling"));
        assertEquals("keep", loaded.get("customLegacyOption").getAsString());
        assertFalse(loaded.get("_firstLaunch").getAsBoolean());
        assertEquals(before, Files.readString(file));
    }
    @Test void namedProfileSavePreservesUnknownOptionsAndDoesNotTouchRoot() throws Exception {
        Path root = directory.resolve("config/zoomify.json"), profile = directory.resolve("profiles/PvP/zoomify.json");
        var global = fixture("zoomify.json"); global.addProperty("initialZoom", 4); ModernSettingsMigration.write(root, global);
        var old = global.deepCopy(); old.add("customNestedData", JsonParser.parseString("{\"a\":[1,2,3]}")); ModernSettingsMigration.write(profile, old);
        String rootBefore = Files.readString(root);
        var changed = fixture("modern-zoomify.json"); changed.addProperty("initialZoom", 15); changed.addProperty("_keybindScrolling", true);
        ZoomProfileStore.save(profile, changed);
        var saved = ModernSettingsMigration.read(profile);
        assertEquals(15, saved.get("initialZoom").getAsInt());
        assertTrue(saved.get("keybindScrolling").getAsBoolean());
        assertEquals(old.get("customNestedData"), saved.get("customNestedData"));
        assertEquals(rootBefore, Files.readString(root));
        String once = Files.readString(profile); ZoomProfileStore.save(profile, changed); assertEquals(once, Files.readString(profile));
    }
    @Test void missingModernFieldsUseDefaultsAndCorruptProfilesStayUntouched() throws Exception {
        Path partial = directory.resolve("profiles/Partial/zoomify.json");
        ModernSettingsMigration.write(partial, JsonParser.parseString("{\"initialZoom\":9}").getAsJsonObject());
        var defaults = fixture("modern-zoomify.json");
        var loaded = ZoomProfileStore.load(partial, defaults);
        assertEquals(defaults.get("zoomInTime"), loaded.get("zoomInTime"));
        Path corrupt = directory.resolve("profiles/Corrupt/zoomify.json"); Files.createDirectories(corrupt.getParent()); Files.writeString(corrupt, "{broken");
        assertThrows(java.io.IOException.class, () -> ZoomProfileStore.load(corrupt, defaults));
        assertThrows(java.io.IOException.class, () -> ZoomProfileStore.save(corrupt, defaults));
        assertEquals("{broken", Files.readString(corrupt));
    }
}
