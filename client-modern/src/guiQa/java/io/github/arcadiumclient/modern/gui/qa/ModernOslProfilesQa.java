// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.modern.client.profile.LegacyOslAliasStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.polyfrost.oneconfig.api.config.v1.CompatSnapshots;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Tree;

final class ModernOslProfilesQa {
    private static final String TOGGLE = "path:itemphysicconfig/toggle_mod";
    private static final String SPEED = "path:itemphysicconfig/falling_rotation_speed";
    private final Path output;
    private final boolean previousEnabled;
    private final float previousSpeed;
    private final Map<Path, byte[]> rootFiles = new LinkedHashMap<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private boolean rootEnabled;
    private float rootSpeed;
    private boolean completed;

    ModernOslProfilesQa(Path output) {
        this.output = output;
        previousEnabled = enabled(); previousSpeed = speed();
    }
    void prepare(String first, Path firstFolder, String second, Path secondFolder) throws Exception {
        ConfigManager.active().save(LegacyOslAliasStore.ITEM_FILE);
        rootEnabled = enabled(); rootSpeed = speed();
        Path root = ConfigManager.active().getFolder();
        for (String name : new String[]{LegacyOslAliasStore.ITEM_FILE, "compat-snapshots.json"}) {
            Path file = root.resolve(name);
            rootFiles.put(file, Files.exists(file) ? Files.readAllBytes(file) : null);
        }
        seed(first, firstFolder, false, .4f);
        seed(second, secondFolder, true, .8f);
        report.put("nativeTree", LegacyOslAliasStore.ITEM_FILE);
        report.put("fixture", "Legacy-only snapshots in new isolated named profiles; no duplicate native tree");
    }
    private void seed(String name, Path folder, boolean enabled, float speed) throws Exception {
        CompatSnapshots.importSerializedValues(name, "itemphysiclite", Map.of(TOGGLE, enabled, SPEED, speed, "qa_unknown", 42));
        CompatSnapshots.importSerializedValues(name, "other-mod", Map.of("untouched", true));
        Files.deleteIfExists(folder.resolve(LegacyOslAliasStore.ITEM_FILE));
        Files.deleteIfExists(folder.resolve("arcadium/modern-native-profiles/legacy-osl-aliases.json"));
    }
    void verify(boolean expectedEnabled, float expectedSpeed, String key) {
        boolean enabled = enabled(); float speed = speed();
        report.put(key, Map.of("enabled", enabled, "rotateSpeed", speed));
        require(enabled == expectedEnabled && Math.abs(speed - expectedSpeed) < .0001f, "Live ItemPhysic values did not match the old OSL profile " + key);
    }
    void edit(float speed) {
        Tree tree = tree();
        tree.getProp("rotateSpeed").setAs(speed);
        require(Math.abs(speed() - speed) < .0001f, "The real ItemPhysic scalar did not accept an edit");
        require(ConfigManager.active().save(LegacyOslAliasStore.ITEM_FILE), "The real ItemPhysic config save failed");
    }
    void saved(Path folder, String label, boolean expectedEnabled, float expectedSpeed) throws Exception {
        JsonObject nativeJson = read(folder.resolve(LegacyOslAliasStore.ITEM_FILE));
        JsonObject snapshot = read(folder.resolve("compat-snapshots.json"));
        JsonObject legacy = snapshot.getAsJsonObject("itemphysiclite");
        require(nativeJson.get("enabled").getAsBoolean() == expectedEnabled && Math.abs(nativeJson.get("rotateSpeed").getAsFloat() - expectedSpeed) < .0001f, "ItemPhysic save did not target named profile " + label);
        require(legacy.get(TOGGLE).getAsBoolean() == expectedEnabled && Math.abs(legacy.get(SPEED).getAsFloat() - expectedSpeed) < .0001f, "Native ItemPhysic edit did not update its old OSL aliases in " + label);
        require(legacy.get("qa_unknown").getAsInt() == 42 && snapshot.getAsJsonObject("other-mod").get("untouched").getAsBoolean(), "OSL alias export discarded unknown snapshot data");
        report.put("saved" + label, Map.of("enabled", nativeJson.get("enabled").getAsBoolean(), "rotateSpeed", nativeJson.get("rotateSpeed").getAsFloat(), "legacySpeed", legacy.get(SPEED).getAsFloat(), "unknownPreserved", true));
        for (String name : new String[]{LegacyOslAliasStore.ITEM_FILE, "compat-snapshots.json"}) {
            Path destination = output.resolve("legacy-profile-fixtures").resolve(label).resolve(name);
            Files.createDirectories(destination.getParent()); Files.copy(folder.resolve(name), destination, StandardCopyOption.REPLACE_EXISTING);
        }
        unchangedRoot();
    }
    void complete() throws Exception {
        verify(rootEnabled, rootSpeed, "rootRestored");
        unchangedRoot(); completed = true;
    }
    Map<String, Object> restored() {
        boolean restored = enabled() == previousEnabled && Math.abs(speed() - previousSpeed) < .0001f;
        report.put("restored", restored); report.put("passed", completed && restored);
        report.put("rootJsonUnchanged", completed); report.put("oldAliasesUpdatedByNativeSave", completed);
        require(restored, "OSL profile QA did not restore the previous native scalar");
        return report;
    }
    Map<String, Object> report() { return report; }
    private void unchangedRoot() throws Exception {
        for (var file : rootFiles.entrySet()) {
            byte[] current = Files.exists(file.getKey()) ? Files.readAllBytes(file.getKey()) : null;
            require(Arrays.equals(file.getValue(), current), "An OSL named-profile save changed root " + file.getKey().getFileName());
        }
    }
    private static Tree tree() {
        require(ConfigManager.active().exists(LegacyOslAliasStore.ITEM_FILE), "The actual ItemPhysic native tree was not registered");
        Tree tree = ConfigManager.active().get(LegacyOslAliasStore.ITEM_FILE);
        require(tree.getProp("enabled") != null && tree.getProp("rotateSpeed") != null, "The actual ItemPhysic tree has no expected native scalar properties");
        return tree;
    }
    private static boolean enabled() { return (Boolean) tree().getProp("enabled").get(); }
    private static float speed() { return ((Number) tree().getProp("rotateSpeed").get()).floatValue(); }
    private static JsonObject read(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
