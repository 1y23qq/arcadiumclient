// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ModernSettingsMigrationTest {
    @TempDir Path directory;
    private Path config() throws IOException { return Files.createDirectories(directory.resolve("config")); }
    private String fixture(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/legacy-settings/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private void setup() throws IOException {
        for (String name : new String[]{"zoomify.json", "polyzoom-keybinds.json", "freelook.json", "behindyouv3.json"}) Files.writeString(config().resolve(name), fixture(name));
    }
    @Test void activeNamedProfileControlsCannotOverwriteTheSharedRootZoomShortcuts() throws Exception {
        setup();
        String activeOptions = "key_zoomify.key.zoom:key.keyboard.v\n";
        Files.writeString(directory.resolve("options.txt"), activeOptions);
        ModernSettingsMigration.migrate(directory, config(), true);
        assertEquals(activeOptions, Files.readString(directory.resolve("options.txt")));
        assertEquals("key.keyboard.c", ModernSettingsMigration.nativeKey(ModernSettingsMigration.read(config().resolve("polyzoom-keybinds.json")).getAsJsonObject("zoomKey")));
        var state = ModernSettingsMigration.read(ModernSettingsMigration.marker(config()));
        assertEquals("key.keyboard.c", state.getAsJsonObject("zoomDeferredRootKeys").get("zoomKey").getAsString());
        ModernSettingsMigration.migrate(directory, config(), false);
        assertTrue(Files.readString(directory.resolve("options.txt")).contains("key_zoomify.key.zoom:key.keyboard.c\n"));
    }
    @Test void explicitRootNativeControlsKeepPrecedenceWhileAnotherProfileIsActive() throws Exception {
        setup();
        Files.writeString(directory.resolve("options.txt"), "key_zoomify.key.zoom:key.keyboard.v\n");
        Files.writeString(config().resolve("minecraft-keybinds.json"), "{\"controls\":{\"zoomify.key.zoom\":\"key.keyboard.r\"}}");
        ModernSettingsMigration.migrate(directory, config(), true);
        assertEquals("key.keyboard.r", ModernSettingsMigration.nativeKey(ModernSettingsMigration.read(config().resolve("polyzoom-keybinds.json")).getAsJsonObject("zoomKey")));
        assertEquals("key_zoomify.key.zoom:key.keyboard.v\n", Files.readString(directory.resolve("options.txt")));
    }
    @Test void savedLegacyFixturesMigrateZoomKeysWithoutChangingSharedFreelookOrSnaplook() throws Exception {
        setup();
        ModernSettingsMigration.migrate(directory, config());
        String options = Files.readString(directory.resolve("options.txt"));
        assertTrue(options.contains("key_zoomify.key.zoom:key.keyboard.c\n"));
        assertTrue(options.contains("key_zoomify.key.zoom.secondary:key.keyboard.f6\n"));
        assertTrue(options.contains("key_zoomify.key.zoom.in:key.keyboard.unknown\n"));
        assertEquals(fixture("polyzoom-keybinds.json"), Files.readString(config().resolve("polyzoom-keybinds.json")));
        assertEquals(fixture("freelook.json"), Files.readString(config().resolve("freelook.json")));
        assertEquals(fixture("behindyouv3.json"), Files.readString(config().resolve("behindyouv3.json")));
        var converted = ModernSettingsMigration.read(config().resolve("zoomify.json"));
        assertFalse(converted.get("_keybindScrolling").getAsBoolean());
        assertEquals(JsonParser.parseString(fixture("zoomify.json")).getAsJsonObject().get("initialZoom"), converted.get("initialZoom"));
        String marker = Files.readString(ModernSettingsMigration.marker(config()));
        ModernSettingsMigration.migrate(directory, config());
        assertEquals(options, Files.readString(directory.resolve("options.txt")));
        assertEquals(marker, Files.readString(ModernSettingsMigration.marker(config())));
    }
    @Test void existingModernKeyAndScrollingPreferenceWinOverLegacyDefaults() throws Exception {
        setup();
        String options = "customUnknown:kept\nkey_zoomify.key.zoom:key.mouse.4\n";
        Files.writeString(directory.resolve("options.txt"), options);
        var zoom = ModernSettingsMigration.read(config().resolve("zoomify.json"));
        zoom.addProperty("_keybindScrolling", true);
        ModernSettingsMigration.write(config().resolve("zoomify.json"), zoom);
        ModernSettingsMigration.migrate(directory, config());
        assertTrue(Files.readString(directory.resolve("options.txt")).startsWith(options));
        assertTrue(ModernSettingsMigration.read(config().resolve("zoomify.json")).get("_keybindScrolling").getAsBoolean());
    }
    @Test void compoundShortcutsStayIntactAndAreScheduledForNativeRuntimeBridge() throws Exception {
        setup();
        var keys = ModernSettingsMigration.read(config().resolve("polyzoom-keybinds.json"));
        keys.getAsJsonObject("zoomKey").addProperty("mods", 2);
        keys.getAsJsonObject("zoomKey").addProperty("durationNanos", 350_000_000L);
        keys.addProperty("unknownFutureField", "keep");
        ModernSettingsMigration.write(config().resolve("polyzoom-keybinds.json"), keys);
        String original = Files.readString(config().resolve("polyzoom-keybinds.json"));
        ModernSettingsMigration.migrate(directory, config());
        var state = ModernSettingsMigration.read(ModernSettingsMigration.marker(config()));
        assertEquals("zoomify.key.zoom", state.getAsJsonObject("zoomBridgedKeys").get("zoomKey").getAsString());
        assertTrue(Files.readString(directory.resolve("options.txt")).contains("key_zoomify.key.zoom:key.keyboard.unknown\n"));
        assertEquals(original, Files.readString(config().resolve("polyzoom-keybinds.json")));
    }
    @Test void laterNativeCodecSaveKeepsUnknownLegacyFieldsAndUpdatesTheSharedAlias() throws Exception {
        setup();
        var old = ModernSettingsMigration.read(config().resolve("zoomify.json"));
        old.add("futureOption", JsonParser.parseString("{\"enabled\":true,\"amount\":12}"));
        ModernSettingsMigration.write(config().resolve("zoomify.json"), old);
        ModernSettingsMigration.migrate(directory, config());
        JsonObject nativeSave = JsonParser.parseString(fixture("modern-zoomify.json")).getAsJsonObject();
        nativeSave.addProperty("_keybindScrolling", true);
        nativeSave.addProperty("initialZoom", 9);
        ModernSettingsMigration.write(config().resolve("zoomify.json"), nativeSave);
        ModernSettingsMigration.afterZoomSave(config().resolve("zoomify.json"), config());
        var restored = ModernSettingsMigration.read(config().resolve("zoomify.json"));
        assertEquals(old.get("futureOption"), restored.get("futureOption"));
        assertTrue(restored.get("keybindScrolling").getAsBoolean());
        assertEquals(9, restored.get("initialZoom").getAsInt());
    }
    @Test void laterVersionSwitchSharesSimpleBindingsInBothDirections() throws Exception {
        setup();
        ModernSettingsMigration.migrate(directory, config());
        var old = ModernSettingsMigration.read(config().resolve("polyzoom-keybinds.json"));
        var changed = new com.google.gson.JsonArray(); changed.add("key.keyboard.v");
        old.getAsJsonObject("zoomKey").add("keyCodes", changed);
        old.addProperty("futureShortcutOption", "preserve");
        ModernSettingsMigration.write(config().resolve("polyzoom-keybinds.json"), old);
        ModernSettingsMigration.migrate(directory, config());
        assertTrue(Files.readString(directory.resolve("options.txt")).contains("key_zoomify.key.zoom:key.keyboard.v\n"));
        Files.writeString(directory.resolve("options.txt"), Files.readString(directory.resolve("options.txt")).replace("key_zoomify.key.zoom:key.keyboard.v", "key_zoomify.key.zoom:key.mouse.right"));
        ModernSettingsMigration.migrate(directory, config());
        var shared = ModernSettingsMigration.read(config().resolve("polyzoom-keybinds.json"));
        assertEquals("key.mouse.right", shared.getAsJsonObject("zoomKey").getAsJsonArray("mouseBtns").get(0).getAsString());
        assertFalse(shared.getAsJsonObject("zoomKey").has("keyCodes"));
        assertEquals("preserve", shared.get("futureShortcutOption").getAsString());
    }
    @Test void legacyScrollingChangesRemainEffectiveAfterTheFirstMigration() throws Exception {
        setup();
        ModernSettingsMigration.migrate(directory, config());
        var oldSave = ModernSettingsMigration.read(config().resolve("zoomify.json"));
        oldSave.remove("_keybindScrolling");
        oldSave.addProperty("keybindScrolling", true);
        ModernSettingsMigration.write(config().resolve("zoomify.json"), oldSave);
        ModernSettingsMigration.migrate(directory, config());
        assertTrue(ModernSettingsMigration.read(config().resolve("zoomify.json")).get("_keybindScrolling").getAsBoolean());
        var modernSave = ModernSettingsMigration.read(config().resolve("zoomify.json"));
        modernSave.addProperty("_keybindScrolling", false);
        modernSave.remove("keybindScrolling");
        ModernSettingsMigration.write(config().resolve("zoomify.json"), modernSave);
        ModernSettingsMigration.afterZoomSave(config().resolve("zoomify.json"), config());
        assertFalse(ModernSettingsMigration.read(config().resolve("zoomify.json")).get("keybindScrolling").getAsBoolean());
    }
    @Test void actualFreelookAndSnaplookSavedSchemasKeepTheirKeysAndEnumProviderNames() throws Exception {
        for (String name : new String[]{"freelook.json", "behindyouv3.json"}) {
            JsonObject old = JsonParser.parseString(fixture(name)).getAsJsonObject();
            JsonObject modern = JsonParser.parseString(fixture("modern-" + name)).getAsJsonObject();
            for (var entry : old.entrySet()) assertTrue(modern.has(entry.getKey()), name + " lost " + entry.getKey());
            if (name.equals("behindyouv3.json")) assertEquals(old.getAsJsonObject("Animation").get("back"), modern.getAsJsonObject("Animation").get("back"));
        }
    }
    @Test void corruptLegacyDataIsNotOverwrittenOrMarkedComplete() throws Exception {
        Files.writeString(config().resolve("polyzoom-keybinds.json"), "{broken");
        assertThrows(IOException.class, () -> ModernSettingsMigration.migrate(directory, config()));
        assertEquals("{broken", Files.readString(config().resolve("polyzoom-keybinds.json")));
        assertFalse(Files.exists(ModernSettingsMigration.marker(config())));
        assertFalse(Files.exists(directory.resolve("options.txt")));
    }
}
