// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.polyfrost.oneconfig.api.config.v1.CompatSnapshots;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;

public final class LegacyOslProfileAliases {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Arcadium/LegacySnapshots");
    private static boolean applying;
    private static String currentProfile;
    private LegacyOslProfileAliases() {}
    public static void beforeLaunch(Path game, Path config) {
        migrateFile(config, true);
        Path profiles = game.resolve("profiles");
        if (!Files.isDirectory(profiles)) return;
        try (var stream = Files.list(profiles)) {
            stream.filter(Files::isDirectory).filter(path -> !path.getFileName().toString().startsWith(".")).forEach(path -> migrateFile(path, false));
        } catch (IOException failure) { LOGGER.warn("Could not inspect legacy named snapshots", failure); }
    }
    private static void migrateFile(Path folder, boolean root) {
        try { LegacyOslAliasStore.plan(folder, root).commit(true); }
        catch (IOException failure) { LOGGER.warn("Kept unreadable legacy snapshots unchanged", failure); }
    }
    public static void beforeItemRegistration() {
        try {
            LegacyOslAliasStore.prepareItemRegistration(ConfigManager.active().getFolder().toAbsolutePath().normalize(), ConfigManager.activeProfile().isEmpty());
        } catch (Exception failure) { LOGGER.warn("Kept unreadable ItemPhysic profile aliases unchanged", failure); }
    }
    public static void initialize() {
        currentProfile = ConfigManager.activeProfile();
        ConfigManager.addTreeRegistrationListener(tree -> {
            if (tree.getID().equals(LegacyOslAliasStore.ITEM_FILE) || tree.getID().equals(LegacyOslAliasStore.TOOLTIP_TREE)) { apply(tree); wire(tree, ""); }
        });
        ConfigManager.addProfileChangeListener(new ConfigManager.ProfileChangeListener() {
            public void onProfileSaving(String profile) { if (profile.equals(currentProfile) && profile.equals(ConfigManager.activeProfile())) exportAll(); }
            public void onProfileChanged(String profile) { apply(null); currentProfile = profile; }
            public void onProfileRenamed(String previous, String next) { if (previous.equals(currentProfile)) currentProfile = next; }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            apply(null);
            for (String id : new String[]{LegacyOslAliasStore.ITEM_FILE, LegacyOslAliasStore.TOOLTIP_TREE}) { Tree live = tree(id); if (live != null) wire(live, ""); }
        });
    }
    private static void apply(Tree registered) {
        if (applying) return;
        applying = true;
        try {
            String profile = ConfigManager.activeProfile();
            var plan = LegacyOslAliasStore.plan(ConfigManager.active().getFolder().toAbsolutePath().normalize(), profile.isEmpty());
            if (!plan.active()) return;
            if (!plan.tooltipChanges().isEmpty()) {
                var values = new LinkedHashMap<String, Object>();
                plan.tooltipChanges().forEach((key, value) -> values.put(key, scalar(value)));
                CompatSnapshots.importSerializedValues(profile, LegacyOslAliasStore.TOOLTIP_TREE, values);
            }
            plan.commit(false);
            Tree item = registered != null && registered.getID().equals(LegacyOslAliasStore.ITEM_FILE) ? registered : tree(LegacyOslAliasStore.ITEM_FILE);
            if (item != null) plan.itemChanges().forEach((key, value) -> set(item.getProp(key), value));
            Tree tooltip = registered != null && registered.getID().equals(LegacyOslAliasStore.TOOLTIP_TREE) ? registered : tree(LegacyOslAliasStore.TOOLTIP_TREE);
            if (tooltip != null) setSnapshotProperties(tooltip, "", plan.tooltipChanges());
        } catch (Exception failure) { LOGGER.warn("Kept unreadable legacy snapshot aliases unchanged", failure); }
        finally { applying = false; }
    }
    private static Tree tree(String id) { return ConfigManager.active().exists(id) ? ConfigManager.active().get(id) : null; }
    private static Object scalar(JsonElement value) { return value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : value.getAsDouble(); }
    private static void setSnapshotProperties(Tree tree, String prefix, Map<String, JsonElement> values) {
        tree.map.forEach((key, node) -> {
            if (node instanceof Tree branch) setSnapshotProperties(branch, prefix + key + "/", values);
            else if (node instanceof Property<?> property) {
                JsonElement value = values.get("path:" + prefix + key);
                if (value != null) set(property, value);
            }
        });
    }
    private static void set(Property<?> property, JsonElement json) {
        if (property == null) return;
        Object current = property.get(), value = scalar(json);
        if (current instanceof Integer) value = json.getAsInt();
        else if (current instanceof Float) value = json.getAsFloat();
        else if (current instanceof Long) value = json.getAsLong();
        property.setAsReferential(value);
    }
    private static void wire(Tree owner, String prefix) {
        Map<String, String> aliases = owner.getID().equals(LegacyOslAliasStore.ITEM_FILE) ? LegacyOslAliasStore.ITEM_KEYS : LegacyOslAliasStore.TOOLTIP_KEYS;
        wire(owner, owner, prefix, aliases);
    }
    private static void wire(Tree owner, Tree branch, String prefix, Map<String, String> aliases) {
        branch.map.forEach((key, node) -> {
            if (node instanceof Tree child) wire(owner, child, prefix + key + "/", aliases);
            else if (node instanceof Property<?> property) {
                String nativeKey = owner.getID().equals(LegacyOslAliasStore.ITEM_FILE) ? key : "path:" + prefix + key;
                if (!aliases.containsValue(nativeKey) || Boolean.TRUE.equals(property.getMetadata("arcadium_legacy_alias_wired"))) return;
                property.addMetadata("arcadium_legacy_alias_wired", true);
                property.addCallback(value -> {
                    if (!applying && !CompatSnapshots.isApplying() && !ConfigManager.isRebindingProfiles() && java.util.Objects.equals(currentProfile, ConfigManager.activeProfile()))
                        export(owner.getID(), Map.of(nativeKey, json(value)), aliases);
                    return false;
                });
            }
        });
    }
    private static JsonElement json(Object value) {
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Number number) return new JsonPrimitive(number);
        throw new IllegalArgumentException("Unsupported legacy alias value");
    }
    private static void exportAll() {
        for (String id : new String[]{LegacyOslAliasStore.ITEM_FILE, LegacyOslAliasStore.TOOLTIP_TREE}) {
            Tree live = tree(id); if (live == null) continue;
            Map<String, String> aliases = id.equals(LegacyOslAliasStore.ITEM_FILE) ? LegacyOslAliasStore.ITEM_KEYS : LegacyOslAliasStore.TOOLTIP_KEYS;
            var values = new LinkedHashMap<String, JsonElement>();
            capture(live, "", id.equals(LegacyOslAliasStore.ITEM_FILE), values);
            export(id, values, aliases);
        }
    }
    private static void capture(Tree tree, String prefix, boolean item, Map<String, JsonElement> values) {
        tree.map.forEach((key, node) -> {
            if (node instanceof Tree child) capture(child, prefix + key + "/", item, values);
            else if (node instanceof Property<?> property && (property.get() instanceof Boolean || property.get() instanceof Number))
                values.put(item ? key : "path:" + prefix + key, json(property.get()));
        });
    }
    private static void export(String nativeId, Map<String, JsonElement> values, Map<String, String> aliases) {
        try {
            var serialized = new LinkedHashMap<String, Object>();
            LegacyOslAliasStore.legacyValues(values, aliases).forEach((key, value) -> serialized.put(key, scalar(value)));
            if (!serialized.isEmpty()) CompatSnapshots.importSerializedValues(ConfigManager.activeProfile(), nativeId.equals(LegacyOslAliasStore.ITEM_FILE) ? "itemphysiclite" : "title.tooltipscroll.config", serialized);
        } catch (Exception failure) { LOGGER.warn("Could not export shared legacy snapshot aliases", failure); }
    }
}
