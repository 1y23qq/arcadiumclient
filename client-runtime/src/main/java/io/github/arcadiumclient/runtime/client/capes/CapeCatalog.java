// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.capes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CapeCatalog {
    public record Entry(String id, String title, String group, String source, String sha256) {
        public String resource() { return "assets/arcadium/capes/official/" + id + ".png"; }
        public String texturePath() { return "capes/official/" + id + ".png"; }
    }
    private static final Map<String, Entry> BY_ID = load();
    private static final List<Entry> ENTRIES = List.copyOf(BY_ID.values());
    private CapeCatalog() {}
    public static List<Entry> entries() { return ENTRIES; }
    public static Entry find(String id) { return id == null ? null : BY_ID.get(id); }

    private static Map<String, Entry> load() {
        var stream = CapeCatalog.class.getClassLoader().getResourceAsStream("assets/arcadium/capes/catalog.json");
        if (stream == null) throw new IllegalStateException("Missing cape catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalStateException("Unknown cape catalog format");
            Map<String, Entry> entries = new LinkedHashMap<>();
            for (JsonElement value : root.getAsJsonArray("entries")) {
                JsonObject item = value.getAsJsonObject();
                Entry entry = new Entry(item.get("id").getAsString(), item.get("title").getAsString(),
                    item.get("group").getAsString(), item.get("source").getAsString(), item.get("sha256").getAsString());
                if (!entry.id().matches("[a-z0-9]+(?:-[a-z0-9]+)*") || entry.title().isBlank()
                    || !entry.sha256().matches("[0-9a-f]{64}") || entries.putIfAbsent(entry.id(), entry) != null)
                    throw new IllegalStateException("Invalid cape catalog entry");
                if (CapeCatalog.class.getClassLoader().getResource(entry.resource()) == null)
                    throw new IllegalStateException("Missing cape texture: " + entry.id());
                if (entries.size() > 128) throw new IllegalStateException("Cape catalog is too large");
            }
            if (entries.isEmpty()) throw new IllegalStateException("Empty cape catalog");
            return java.util.Collections.unmodifiableMap(entries);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not read cape catalog", error);
        }
    }
}
