// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModernSettingsMigration {
    public static final Map<String, String> ZOOM_KEYS = new LinkedHashMap<>();
    static {
        ZOOM_KEYS.put("zoomKey", "zoomify.key.zoom");
        ZOOM_KEYS.put("secondaryZoomKey", "zoomify.key.zoom.secondary");
        ZOOM_KEYS.put("zoomInKey", "zoomify.key.zoom.in");
        ZOOM_KEYS.put("zoomOutKey", "zoomify.key.zoom.out");
    }
    private ModernSettingsMigration() {}
    public static Path marker(Path config) { return config.resolve("arcadium/modern-counterparts.json"); }
    public static void migrate(Path game, Path config) throws IOException { migrate(game, config, false); }
    public static void migrate(Path game, Path config, boolean activeNamedProfile) throws IOException {
        Path marker = marker(config);
        JsonObject state = Files.exists(marker) ? read(marker) : new JsonObject();
        if (state.has("version") && state.get("version").getAsInt() != 1) throw new IOException("Unsupported settings migration version.");
        Path zoomFile = config.resolve("zoomify.json"), keysFile = config.resolve("polyzoom-keybinds.json");
        JsonObject zoom = Files.exists(zoomFile) ? read(zoomFile) : null;
        JsonObject keys = Files.exists(keysFile) ? read(keysFile) : null;
        if (state.isEmpty() && keys == null && (zoom == null || !zoom.has("keybindScrolling") && !zoom.has("_keybindScrolling"))) return;
        JsonObject previousState = state.deepCopy();
        JsonObject previousZoom = zoom == null ? null : zoom.deepCopy();
        JsonObject previousKeys = keys == null ? null : keys.deepCopy();
        state.addProperty("version", 1);
        state.addProperty("minecraft", "1.21.11");
        JsonObject bridge = object(state, "zoomBridgedKeys");
        JsonObject tracking = object(state, "zoomKeyState");
        JsonObject deferred = activeNamedProfile ? new JsonObject() : object(state, "zoomDeferredRootKeys");
        Path controlsFile = config.resolve("minecraft-keybinds.json");
        JsonObject rootControls = activeNamedProfile && Files.isRegularFile(controlsFile) ? object(read(controlsFile), "controls") : null;
        if (keys != null && !state.has("zoomOriginalKeys")) state.add("zoomOriginalKeys", keys.deepCopy());
        List<String> options = readOptions(game.resolve("options.txt"));
        boolean optionsChanged = false;
        if (keys != null) for (var entry : ZOOM_KEYS.entrySet()) {
            if (!keys.has(entry.getKey()) || !keys.get(entry.getKey()).isJsonObject()) continue;
            String option = "key_" + entry.getValue() + ":";
            JsonObject binding = keys.getAsJsonObject(entry.getKey());
            String current = options.stream().filter(line -> line.startsWith(option)).map(line -> line.substring(option.length())).findFirst().orElse(null);
            String optionValue = current;
            if (activeNamedProfile) current = rootControls != null && rootControls.has(entry.getValue()) ? rootControls.get(entry.getValue()).getAsString() : null;
            else if (deferred.has(entry.getKey())) current = deferred.get(entry.getKey()).getAsString();
            JsonObject last = tracking.has(entry.getKey()) ? tracking.getAsJsonObject(entry.getKey()) : null;
            String fingerprint = fingerprint(binding);
            boolean legacyChanged = last == null || !fingerprint.equals(last.get("legacy").getAsString());
            boolean nativeChanged = current != null && (last == null || !current.equals(last.get("native").getAsString()));
            if (current == null || legacyChanged && !nativeChanged) {
                current = nativeKey(binding);
                if (current == null) { current = "key.keyboard.unknown"; bridge.addProperty(entry.getKey(), entry.getValue()); }
                else bridge.remove(entry.getKey());
                String replacement = option + current;
                int at = -1;
                for (int i = 0; i < options.size(); i++) if (options.get(i).startsWith(option)) { at = i; break; }
                if (at < 0) options.add(replacement); else options.set(at, replacement);
                optionsChanged = true;
            } else if (nativeChanged) {
                if (!applyNativeKey(binding, current)) continue;
                bridge.remove(entry.getKey());
            }
            JsonObject saved = new JsonObject();
            saved.addProperty("legacy", fingerprint(binding));
            saved.addProperty("native", current);
            tracking.add(entry.getKey(), saved);
            if (activeNamedProfile) deferred.addProperty(entry.getKey(), current);
            else if (deferred.has(entry.getKey()) && !current.equals(optionValue)) {
                String replacement = option + current;
                int at = -1;
                for (int i = 0; i < options.size(); i++) if (options.get(i).startsWith(option)) { at = i; break; }
                if (at < 0) options.add(replacement); else options.set(at, replacement);
                optionsChanged = true;
            }
        }
        if (activeNamedProfile) state.add("zoomDeferredRootKeys", deferred);
        else state.remove("zoomDeferredRootKeys");
        state.add("zoomBridgedKeys", bridge);
        state.add("zoomKeyState", tracking);
        if (zoom != null && (zoom.has("keybindScrolling") || zoom.has("_keybindScrolling"))) {
            syncScrolling(zoom, state);
            zoom.addProperty("_firstLaunch", false);
            JsonObject preserved = object(state, "zoomOriginal");
            for (var entry : zoom.entrySet()) preserved.add(entry.getKey(), entry.getValue().deepCopy());
            state.add("zoomOriginal", preserved);
        }
        if (optionsChanged && !activeNamedProfile) write(game.resolve("options.txt"), String.join("\n", options) + "\n");
        if (zoom != null && !zoom.equals(previousZoom)) write(zoomFile, zoom);
        if (keys != null && !keys.equals(previousKeys)) write(keysFile, keys);
        if (!state.equals(previousState)) write(marker, state);
    }
    private static JsonObject object(JsonObject root, String key) { return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject(); }
    public static String fingerprint(JsonObject binding) {
        JsonObject value = new JsonObject();
        for (String key : List.of("class", "keyCodes", "mouseBtns", "mods", "durationNanos")) if (binding.has(key)) value.add(key, binding.get(key));
        return value.toString();
    }
    private static void syncScrolling(JsonObject zoom, JsonObject state) {
        var legacy = zoom.get("keybindScrolling");
        var nativeValue = zoom.get("_keybindScrolling");
        var last = state.get("zoomSharedScrolling");
        if (nativeValue == null || legacy != null && last != null && !legacy.equals(last) && nativeValue.equals(last)) nativeValue = legacy;
        if (nativeValue == null || !nativeValue.isJsonPrimitive() || !nativeValue.getAsJsonPrimitive().isBoolean()) return;
        zoom.add("_keybindScrolling", nativeValue.deepCopy());
        zoom.add("keybindScrolling", nativeValue.deepCopy());
        state.add("zoomSharedScrolling", nativeValue.deepCopy());
    }
    public static String nativeKey(JsonObject binding) {
        if (number(binding, "mods") != 0 || number(binding, "durationNanos") != 0) return null;
        var keys = binding.has("keyCodes") && binding.get("keyCodes").isJsonArray() ? binding.getAsJsonArray("keyCodes") : null;
        var mouse = binding.has("mouseBtns") && binding.get("mouseBtns").isJsonArray() ? binding.getAsJsonArray("mouseBtns") : null;
        int count = (keys == null ? 0 : keys.size()) + (mouse == null ? 0 : mouse.size());
        if (count == 0) return "key.keyboard.unknown";
        if (count != 1) return null;
        var input = keys != null && !keys.isEmpty() ? keys.get(0) : mouse.get(0);
        if (!input.isJsonPrimitive() || !input.getAsJsonPrimitive().isString()) return null;
        String name = input.getAsString();
        return validNativeInput(name) ? name : null;
    }
    private static boolean validNativeInput(String name) {
        if (name.matches("key\\.mouse\\.(left|right|middle|[4-8])")) return true;
        if (!name.startsWith("key.keyboard.")) return false;
        String key = name.substring("key.keyboard.".length());
        if (key.matches("[a-z0-9]|f([1-9]|1[0-9]|2[0-5])|keypad\\.([0-9]|decimal|divide|multiply|subtract|add|enter|equal)|(left|right)\\.(shift|control|alt|win)")) return true;
        return List.of("unknown", "space", "apostrophe", "comma", "minus", "period", "slash", "semicolon", "equal", "left.bracket", "backslash", "right.bracket", "grave.accent", "world.1", "world.2", "escape", "enter", "tab", "backspace", "insert", "delete", "right", "left", "down", "up", "page.up", "page.down", "home", "end", "caps.lock", "scroll.lock", "num.lock", "print.screen", "pause", "menu").contains(key);
    }
    public static boolean applyNativeKey(JsonObject binding, String current) {
        if (!validNativeInput(current)) return false;
        for (String key : List.of("keyCodes", "mouseBtns", "mods", "durationNanos")) binding.remove(key);
        if (!current.equals("key.keyboard.unknown")) {
            var values = new com.google.gson.JsonArray(); values.add(current);
            binding.add(current.startsWith("key.mouse.") ? "mouseBtns" : "keyCodes", values);
        }
        return true;
    }
    private static long number(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber() ? object.get(key).getAsLong() : 0;
    }
    public static void afterZoomSave(Path zoomFile, Path config) throws IOException {
        if (!zoomFile.toAbsolutePath().normalize().equals(config.resolve("zoomify.json").toAbsolutePath().normalize()) || !Files.isRegularFile(zoomFile)) return;
        JsonObject current = read(zoomFile), previous = current.deepCopy();
        Path marker = marker(config);
        if (Files.isRegularFile(marker)) {
            JsonObject state = read(marker);
            JsonObject originalState = state.deepCopy();
            if (state.has("zoomOriginal") && state.get("zoomOriginal").isJsonObject()) {
                for (var entry : state.getAsJsonObject("zoomOriginal").entrySet())
                    if (!current.has(entry.getKey())) current.add(entry.getKey(), entry.getValue().deepCopy());
            }
            if (current.has("_keybindScrolling")) state.add("zoomSharedScrolling", current.get("_keybindScrolling").deepCopy());
            if (!state.equals(originalState)) write(marker, state);
        }
        if (current.has("_keybindScrolling")) current.add("keybindScrolling", current.get("_keybindScrolling").deepCopy());
        if (!current.equals(previous)) write(zoomFile, current);
    }
    public static JsonObject read(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 4 * 1024 * 1024) throw new IOException("Invalid settings file.");
        try {
            var value = JsonParser.parseString(Files.readString(file));
            if (!value.isJsonObject()) throw new IOException("Invalid settings document.");
            return value.getAsJsonObject();
        } catch (RuntimeException invalid) { throw new IOException("Could not read settings document.", invalid); }
    }
    private static List<String> readOptions(Path file) throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        if (!Files.isRegularFile(file) || Files.size(file) > 4 * 1024 * 1024) throw new IOException("Invalid Minecraft options.");
        return new ArrayList<>(Files.readAllLines(file));
    }
    public static void write(Path file, JsonObject value) throws IOException { write(file, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n"); }
    private static void write(Path file, String content) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), ".arcadium-migration-", ".tmp");
        try {
            Files.writeString(temporary, content);
            try { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
