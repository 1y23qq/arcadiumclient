// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.shared.debug;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DebugLayout {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public record Definition(String id, String title, Map<String, String> lines, Map<String, Integer> colors) { }
    private static final Map<String, Definition> DEFINITIONS = definitions();
    public final List<Module> left = new ArrayList<>(), right = new ArrayList<>();
    public static final class Module {
        public String key = UUID.randomUUID().toString(), kind;
        public boolean enabled = true, memoryColorToggle = true;
        public int emptyLines = 1;
        public String timeFormat = "HH:mm:ss";
        public final Map<String, Boolean> lines = new LinkedHashMap<>();
        public final Map<String, Integer> colors = new LinkedHashMap<>();
        public Module(String kind) {
            Definition definition = DEFINITIONS.get(kind);
            if (definition == null) throw new IllegalArgumentException("Unknown debug module: " + kind);
            this.kind = kind; definition.lines.keySet().forEach(id -> lines.put(id, true)); colors.putAll(definition.colors);
        }
        public int color(String name) { return 0xFF000000 | colors.getOrDefault(name, 0xE0E0E0); }
        public Definition definition() { return DEFINITIONS.get(kind); }
    }
    public static Collection<Definition> catalog() { return DEFINITIONS.values(); }
    private static Map<String, Definition> definitions() {
        try (InputStream in = DebugLayout.class.getResourceAsStream("/assets/arcadium/custom-f3/modules.json")) {
            if (in == null) throw new IllegalStateException("Missing debug module definitions");
            Definition[] values = JSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), Definition[].class);
            Map<String, Definition> result = new LinkedHashMap<>();
            for (Definition value : values) result.put(value.id, value);
            return Collections.unmodifiableMap(result);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    public static DebugLayout defaults() {
        DebugLayout layout = new DebugLayout();
        for (String kind : List.of("minecraft", "fps", "graphics", "server", "coords", "chunks", "location", "entity", "sound", "help", "empty", "miscleft")) layout.left.add(new Module(kind));
        for (String kind : List.of("system", "miscright", "empty", "target")) layout.right.add(new Module(kind));
        return layout;
    }
    public static DebugLayout decode(String text) {
        if (text == null || text.isBlank()) return defaults();
        if (text.length() > 512 * 1024) throw new IllegalArgumentException("Debug layout exceeds 512 KiB");
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        DebugLayout result = new DebugLayout(); Set<String> keys = new HashSet<>();
        for (String side : List.of("left", "right")) {
            JsonArray modules = root.getAsJsonArray(side);
            if (modules == null || modules.size() > 64) throw new IllegalArgumentException("Invalid debug column");
            List<Module> destination = side.equals("left") ? result.left : result.right;
            for (JsonElement element : modules) {
                JsonObject value = element.getAsJsonObject(); Module module = new Module(value.get("kind").getAsString());
                if (value.has("key")) module.key = UUID.fromString(value.get("key").getAsString()).toString();
                if (!keys.add(module.key)) throw new IllegalArgumentException("Duplicate debug module");
                module.enabled = !value.has("enabled") || value.get("enabled").getAsBoolean();
                module.memoryColorToggle = !value.has("memoryColorToggle") || value.get("memoryColorToggle").getAsBoolean();
                module.emptyLines = value.has("emptyLines") ? Math.max(0, Math.min(20, value.get("emptyLines").getAsInt())) : 1;
                if (value.has("timeFormat")) {
                    module.timeFormat = value.get("timeFormat").getAsString();
                    if (module.timeFormat.length() > 80) throw new IllegalArgumentException("Time format is too long");
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(module.timeFormat));
                }
                if (value.has("lines")) value.getAsJsonObject("lines").entrySet().forEach(e -> { if (module.lines.containsKey(e.getKey())) module.lines.put(e.getKey(), e.getValue().getAsBoolean()); });
                if (value.has("colors")) value.getAsJsonObject("colors").entrySet().forEach(e -> { if (module.colors.containsKey(e.getKey())) module.colors.put(e.getKey(), e.getValue().getAsInt() & 0xFFFFFF); });
                destination.add(module);
            }
        }
        return result;
    }
    public String encode() { return JSON.toJson(this); }
    public List<Module> all() { List<Module> result = new ArrayList<>(left); result.addAll(right); return result; }
    public static String colorLabel(String value) { return value.replaceAll("([a-z])([A-Z])", "$1 $2").replaceFirst("^color ", "") + " color"; }
}
