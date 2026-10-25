// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LegacyOslAliasStore {
    public static final String ITEM_FILE = "itemphysicliteconfig.mixins.json";
    public static final String TOOLTIP_TREE = "tooltipscroll";
    public static final Map<String, String> ITEM_KEYS = Map.of(
        "path:itemphysicconfig/toggle_mod", "enabled",
        "path:itemphysicconfig/falling_rotation_speed", "rotateSpeed"
    );
    public static final Map<String, String> TOOLTIP_KEYS;
    static {
        var keys = new LinkedHashMap<String, String>();
        String[][] names = {{"canscroll", "canscroll"}, {"usewasd", "usewasd"}, {"startontop", "startontop"}, {"resetonunlock", "resetonunlock"},
            {"uselshift", "uselshift"}, {"invertxscroll", "invertxscroll"}, {"invertyscroll", "invertyscroll"}, {"scrollspeed", "scrollspeed"},
            {"keyboardscrollspeed", "scrollspeedkeys"}, {"scrollsmoothness", "smoothness"}, {"matrixcompatibilitymode", "matrix_compatibility"}};
        for (String[] pair : names) keys.put("path:general/" + pair[0], "path:category.tooltipscroll.general/entry.tooltipscroll." + pair[1]);
        TOOLTIP_KEYS = Map.copyOf(keys);
    }
    public record Plan(Path folder, JsonObject snapshot, JsonObject item, JsonObject marker, Map<String, JsonElement> itemChanges, Map<String, JsonElement> tooltipChanges, boolean active) {
        public void commit(boolean snapshots) throws IOException {
            if (!active) return;
            if (!itemChanges.isEmpty()) writeChanged(folder.resolve(ITEM_FILE), item);
            if (snapshots && !tooltipChanges.isEmpty()) writeChanged(folder.resolve("compat-snapshots.json"), snapshot);
            writeChanged(markerPath(folder), marker);
        }
    }
    private LegacyOslAliasStore() {}

    public static void prepareItemRegistration(Path folder, boolean root) throws IOException {
        Plan plan = plan(folder, root);
        if (!plan.active || object(plan.marker, "itemLegacy").isEmpty()) return;
        JsonObject marker = read(markerPath(folder));
        marker.addProperty("version", 1);
        marker.add("itemLegacy", plan.marker.get("itemLegacy").deepCopy());
        marker.add("itemNative", plan.marker.get("itemNative").deepCopy());
        if (!plan.itemChanges.isEmpty()) writeChanged(folder.resolve(ITEM_FILE), plan.item);
        writeChanged(markerPath(folder), marker);
    }

    public static Map<String, JsonElement> legacyValues(Map<String, JsonElement> nativeValues, Map<String, String> aliases) throws IOException {
        var result = new LinkedHashMap<String, JsonElement>();
        for (var alias : aliases.entrySet()) {
            JsonElement value = nativeValues.get(alias.getValue());
            if (value == null) continue;
            validate(alias.getKey(), value);
            result.put(alias.getKey(), value.deepCopy());
        }
        return result;
    }

    public static Plan plan(Path folder, boolean root) throws IOException {
        JsonObject snapshot = read(folder.resolve("compat-snapshots.json"));
        JsonObject itemSource = object(snapshot, "itemphysiclite").deepCopy();
        if (root && itemSource.isEmpty() && Files.isRegularFile(folder.resolve("oitemphysic.json"))) {
            JsonObject options = object(object(read(folder.resolve("oitemphysic.json")), "options"), "ItemPhysicConfig");
            if (options.has("Toggle mod")) itemSource.add("path:itemphysicconfig/toggle_mod", options.get("Toggle mod"));
            if (options.has("Falling rotation speed")) itemSource.add("path:itemphysicconfig/falling_rotation_speed", options.get("Falling rotation speed"));
        }
        JsonObject tooltipSource = object(snapshot, "title.tooltipscroll.config");
        if (itemSource.isEmpty() && tooltipSource.isEmpty()) return new Plan(folder, snapshot, new JsonObject(), new JsonObject(), Map.of(), Map.of(), false);
        JsonObject marker = read(markerPath(folder)), item = read(folder.resolve(ITEM_FILE));
        JsonObject tooltip = object(snapshot, TOOLTIP_TREE).deepCopy();
        Map<String, JsonElement> itemChanges = migrate(itemSource, item, ITEM_KEYS, object(marker, "itemLegacy"), object(marker, "itemNative"));
        Map<String, JsonElement> tooltipChanges = migrate(tooltipSource, tooltip, TOOLTIP_KEYS, object(marker, "tooltipLegacy"), object(marker, "tooltipNative"));
        if (!tooltipChanges.isEmpty()) snapshot.add(TOOLTIP_TREE, tooltip);
        marker.addProperty("version", 1);
        marker.add("itemLegacy", itemSource.deepCopy()); marker.add("itemNative", select(item, ITEM_KEYS.values()));
        marker.add("tooltipLegacy", tooltipSource.deepCopy()); marker.add("tooltipNative", select(tooltip, TOOLTIP_KEYS.values()));
        return new Plan(folder, snapshot, item, marker, Map.copyOf(itemChanges), Map.copyOf(tooltipChanges), true);
    }
    private static Map<String, JsonElement> migrate(JsonObject source, JsonObject target, Map<String, String> keys, JsonObject previousSource, JsonObject previousTarget) throws IOException {
        var changes = new LinkedHashMap<String, JsonElement>();
        for (var key : keys.entrySet()) {
            JsonElement value = source.get(key.getKey());
            if (value == null) continue;
            validate(key.getKey(), value);
            boolean sourceChanged = previousSource.has(key.getKey()) && !value.equals(previousSource.get(key.getKey()));
            boolean targetChanged = previousTarget.has(key.getValue()) && !java.util.Objects.equals(target.get(key.getValue()), previousTarget.get(key.getValue()));
            if (!target.has(key.getValue()) || sourceChanged && !targetChanged) {
                if (!value.equals(target.get(key.getValue()))) { target.add(key.getValue(), value.deepCopy()); changes.put(key.getValue(), value.deepCopy()); }
            }
        }
        return changes;
    }
    private static void validate(String key, JsonElement value) throws IOException {
        if (!value.isJsonPrimitive()) throw new IOException("Invalid legacy OSL scalar: " + key);
        boolean number = key.contains("rotation_speed") || key.endsWith("scrollspeed") || key.endsWith("keyboardscrollspeed") || key.endsWith("scrollsmoothness");
        if (number) {
            if (!value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble())) throw new IOException("Invalid legacy OSL number: " + key);
            if (key.contains("rotation_speed") && Math.abs(value.getAsDouble()) > Float.MAX_VALUE) throw new IOException("Legacy rotation speed exceeds float range");
            if (key.endsWith("scrollsmoothness") && (value.getAsDouble() < .05 || value.getAsDouble() > 1)) throw new IOException("Legacy tooltip smoothness is out of range");
            if (key.endsWith("scrollspeed") && (value.getAsDouble() < 1 || value.getAsDouble() > Integer.MAX_VALUE || value.getAsDouble() != Math.rint(value.getAsDouble()))) throw new IOException("Legacy tooltip speed is out of range");
        } else if (!value.getAsJsonPrimitive().isBoolean()) throw new IOException("Invalid legacy OSL boolean: " + key);
    }
    private static JsonObject select(JsonObject value, java.util.Collection<String> keys) { JsonObject result = new JsonObject(); for (String key : keys) if (value.has(key)) result.add(key, value.get(key).deepCopy()); return result; }
    private static JsonObject object(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonObject() ? value.getAsJsonObject(key) : new JsonObject(); }
    private static Path markerPath(Path folder) { return folder.resolve("arcadium/modern-native-profiles/legacy-osl-aliases.json"); }
    private static JsonObject read(Path file) throws IOException { return Files.exists(file) ? ModernSettingsMigration.read(file) : new JsonObject(); }
    private static void writeChanged(Path file, JsonObject value) throws IOException { if (!Files.exists(file) || !ModernSettingsMigration.read(file).equals(value)) ModernSettingsMigration.write(file, value); }
}
