// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.backend.impl.FileBackend;
import org.polyfrost.oneconfig.api.config.v1.serialize.impl.NightConfigSerializer;
import static org.junit.jupiter.api.Assertions.*;

public class LegacyOslAliasStoreTest {
    @TempDir Path folder;
    private JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private JsonObject legacy() {
        return json("{\"itemphysiclite\":{\"path:itemphysicconfig/toggle_mod\":false,\"path:itemphysicconfig/falling_rotation_speed\":0.4,\"unknown\":42},\"title.tooltipscroll.config\":{\"path:general/canscroll\":false,\"path:general/keyboardscrollspeed\":11,\"path:general/scrollsmoothness\":0.2},\"other-mod\":{\"untouched\":true}}");
    }
    @Test void importsActualLegacySnapshotPathsWithoutRemovingOldOrUnknownData() throws Exception {
        var old = legacy();
        ModernSettingsMigration.write(folder.resolve("compat-snapshots.json"), old);
        var plan = LegacyOslAliasStore.plan(folder, false); plan.commit(true);
        var item = ModernSettingsMigration.read(folder.resolve(LegacyOslAliasStore.ITEM_FILE));
        assertFalse(item.get("enabled").getAsBoolean());
        assertEquals(.4f, item.get("rotateSpeed").getAsFloat());
        var snapshots = ModernSettingsMigration.read(folder.resolve("compat-snapshots.json"));
        assertEquals(old.get("itemphysiclite"), snapshots.get("itemphysiclite"));
        assertEquals(old.get("title.tooltipscroll.config"), snapshots.get("title.tooltipscroll.config"));
        assertEquals(old.get("other-mod"), snapshots.get("other-mod"));
        var tooltip = snapshots.getAsJsonObject("tooltipscroll");
        assertFalse(tooltip.get("path:category.tooltipscroll.general/entry.tooltipscroll.canscroll").getAsBoolean());
        assertEquals(11, tooltip.get("path:category.tooltipscroll.general/entry.tooltipscroll.scrollspeedkeys").getAsInt());
        assertEquals(.2, tooltip.get("path:category.tooltipscroll.general/entry.tooltipscroll.smoothness").getAsDouble());
        byte[] unchanged = Files.readAllBytes(folder.resolve("compat-snapshots.json"));
        assertTrue(LegacyOslAliasStore.plan(folder, false).tooltipChanges().isEmpty());
        LegacyOslAliasStore.plan(folder, false).commit(true);
        assertArrayEquals(unchanged, Files.readAllBytes(folder.resolve("compat-snapshots.json")));
    }
    @Test void nativeChoicesWinConflictsButLaterLegacyEditsStillApply() throws Exception {
        ModernSettingsMigration.write(folder.resolve("compat-snapshots.json"), legacy());
        ModernSettingsMigration.write(folder.resolve(LegacyOslAliasStore.ITEM_FILE), json("{\"enabled\":true,\"rotateSpeed\":2.0,\"nativeOnly\":7}"));
        LegacyOslAliasStore.plan(folder, false).commit(true);
        var nativeValue = ModernSettingsMigration.read(folder.resolve(LegacyOslAliasStore.ITEM_FILE));
        assertTrue(nativeValue.get("enabled").getAsBoolean()); assertEquals(2, nativeValue.get("rotateSpeed").getAsDouble());
        var old = ModernSettingsMigration.read(folder.resolve("compat-snapshots.json"));
        old.getAsJsonObject("itemphysiclite").addProperty("path:itemphysicconfig/falling_rotation_speed", .75);
        ModernSettingsMigration.write(folder.resolve("compat-snapshots.json"), old);
        LegacyOslAliasStore.plan(folder, false).commit(true);
        assertEquals(.75, ModernSettingsMigration.read(folder.resolve(LegacyOslAliasStore.ITEM_FILE)).get("rotateSpeed").getAsDouble());
        old.getAsJsonObject("itemphysiclite").addProperty("path:itemphysicconfig/falling_rotation_speed", .6);
        ModernSettingsMigration.write(folder.resolve("compat-snapshots.json"), old);
        nativeValue.addProperty("rotateSpeed", 1.5);
        ModernSettingsMigration.write(folder.resolve(LegacyOslAliasStore.ITEM_FILE), nativeValue);
        LegacyOslAliasStore.plan(folder, false).commit(true);
        assertEquals(1.5, ModernSettingsMigration.read(folder.resolve(LegacyOslAliasStore.ITEM_FILE)).get("rotateSpeed").getAsDouble());
        assertEquals(7, ModernSettingsMigration.read(folder.resolve(LegacyOslAliasStore.ITEM_FILE)).get("nativeOnly").getAsInt());
    }
    @Test void liveCachePlanDoesNotDirectlyRewriteItsSnapshotFile() throws Exception {
        ModernSettingsMigration.write(folder.resolve("compat-snapshots.json"), legacy());
        byte[] before = Files.readAllBytes(folder.resolve("compat-snapshots.json"));
        var plan = LegacyOslAliasStore.plan(folder, false);
        assertFalse(plan.tooltipChanges().isEmpty()); plan.commit(false);
        assertArrayEquals(before, Files.readAllBytes(folder.resolve("compat-snapshots.json")));
    }
    @Test void rootOslFallbackDoesNotCreateNamedOverridesAndCorruptionIsPreserved() throws Exception {
        ModernSettingsMigration.write(folder.resolve("oitemphysic.json"), json("{\"config\":\"ItemPhysicLite\",\"version\":0,\"options\":{\"ItemPhysicConfig\":{\"Toggle mod\":true,\"Falling rotation speed\":1.25}}}"));
        assertFalse(LegacyOslAliasStore.plan(folder, false).active());
        LegacyOslAliasStore.plan(folder, true).commit(true);
        assertEquals(1.25, ModernSettingsMigration.read(folder.resolve(LegacyOslAliasStore.ITEM_FILE)).get("rotateSpeed").getAsDouble());
        Files.writeString(folder.resolve("compat-snapshots.json"), "{broken");
        byte[] original = Files.readAllBytes(folder.resolve(LegacyOslAliasStore.ITEM_FILE));
        assertThrows(java.io.IOException.class, () -> LegacyOslAliasStore.plan(folder, true));
        assertEquals("{broken", Files.readString(folder.resolve("compat-snapshots.json")));
        assertArrayEquals(original, Files.readAllBytes(folder.resolve(LegacyOslAliasStore.ITEM_FILE)));
    }
    @Test void nativeScalarEditsExportToExactOldKeysAndRetainTheirUnits() throws Exception {
        var item = json("{\"enabled\":false,\"rotateSpeed\":0.75,\"unrelated\":99}");
        var itemValues = LegacyOslAliasStore.legacyValues(item.asMap(), LegacyOslAliasStore.ITEM_KEYS);
        assertEquals(2, itemValues.size());
        assertFalse(itemValues.get("path:itemphysicconfig/toggle_mod").getAsBoolean());
        assertEquals(.75, itemValues.get("path:itemphysicconfig/falling_rotation_speed").getAsDouble());
        String key = "path:category.tooltipscroll.general/entry.tooltipscroll.scrollspeedkeys";
        var tooltip = LegacyOslAliasStore.legacyValues(Map.of(key, json("{\"value\":11}").get("value")), LegacyOslAliasStore.TOOLTIP_KEYS);
        assertEquals(11, tooltip.get("path:general/keyboardscrollspeed").getAsInt());
        assertThrows(java.io.IOException.class, () -> LegacyOslAliasStore.legacyValues(Map.of("rotateSpeed", json("{\"value\":\"wrong\"}").get("value")), LegacyOslAliasStore.ITEM_KEYS));
    }
    @Test void lateCreatedNamedProfileImportsBeforeRealBackendCreatesDefaultValues() throws Exception {
        Path root = folder.resolve("config"), late = folder.resolve("profiles/CreatedLater");
        ModernSettingsMigration.write(root.resolve("compat-snapshots.json"), json("{\"untouched\":{\"root\":true}}"));
        byte[] rootBefore = Files.readAllBytes(root.resolve("compat-snapshots.json"));
        ModernSettingsMigration.write(late.resolve("compat-snapshots.json"), legacy());
        byte[] snapshotBefore = Files.readAllBytes(late.resolve("compat-snapshots.json"));
        assertFalse(Files.exists(late.resolve(LegacyOslAliasStore.ITEM_FILE)));
        LegacyOslAliasStore.prepareItemRegistration(late, false);
        Tree live = registerNativeItem(late);
        assertFalse((Boolean) live.getProp("enabled").get());
        assertEquals(.4f, ((Number) live.getProp("rotateSpeed").get()).floatValue());
        assertArrayEquals(rootBefore, Files.readAllBytes(root.resolve("compat-snapshots.json")));
        assertArrayEquals(snapshotBefore, Files.readAllBytes(late.resolve("compat-snapshots.json")));
        LegacyOslAliasStore.prepareItemRegistration(late, false);
        assertEquals(.4f, ((Number) registerNativeItem(late).getProp("rotateSpeed").get()).floatValue());
    }
    @Test void preRegistrationRetainsExplicitNativeChoiceAndUnrelatedMarkerState() throws Exception {
        ModernSettingsMigration.write(folder.resolve("compat-snapshots.json"), legacy());
        ModernSettingsMigration.write(folder.resolve(LegacyOslAliasStore.ITEM_FILE), json("{\"enabled\":true,\"rotateSpeed\":2,\"nativeOnly\":17}"));
        Path marker = folder.resolve("arcadium/modern-native-profiles/legacy-osl-aliases.json");
        ModernSettingsMigration.write(marker, json("{\"tooltipNative\":{\"untouched\":true},\"unknown\":19}"));
        LegacyOslAliasStore.prepareItemRegistration(folder, false);
        Tree live = registerNativeItem(folder);
        assertTrue((Boolean) live.getProp("enabled").get());
        assertEquals(2f, ((Number) live.getProp("rotateSpeed").get()).floatValue());
        assertEquals(17, ModernSettingsMigration.read(folder.resolve(LegacyOslAliasStore.ITEM_FILE)).get("nativeOnly").getAsInt());
        var saved = ModernSettingsMigration.read(marker);
        assertEquals(19, saved.get("unknown").getAsInt());
        assertTrue(saved.getAsJsonObject("tooltipNative").get("untouched").getAsBoolean());
    }
    private Tree registerNativeItem(Path directory) {
        Tree tree = Tree.tree(LegacyOslAliasStore.ITEM_FILE);
        tree.put(Properties.simple("enabled", null, null, true, Boolean.class));
        tree.put(Properties.simple("rotateSpeed", null, null, 1f, Float.class));
        return new FileBackend(directory, NightConfigSerializer.JSON).register(tree).get();
    }
}
