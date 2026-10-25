// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class HeightLimitMaps {
    private HeightLimitMaps() { }

    public static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public static Map<String, Integer> parse(String json) {
        var root = JsonParser.parseString(json);
        if (!root.isJsonArray()) throw new IllegalArgumentException("Invalid height-limit data.");
        var maps = new LinkedHashMap<String, Integer>();
        if (root.getAsJsonArray().size() > 10_000) throw new IllegalArgumentException("Too many height-limit maps.");
        for (var entry : root.getAsJsonArray()) {
            if (!entry.isJsonObject()) continue;
            var value = entry.getAsJsonObject();
            try {
                if (!value.get("gameType").getAsString().equals("BEDWARS")) continue;
                String name = key(value.get("name").getAsString());
                int limit = value.get("maxBuild").getAsInt();
                if (!name.isEmpty() && name.length() <= 128 && limit > 0 && limit <= 4096) maps.put(name, limit);
            } catch (IllegalStateException | UnsupportedOperationException | NumberFormatException | NullPointerException ignored) { }
        }
        if (maps.isEmpty()) throw new IllegalArgumentException("Height-limit data contains no supported maps.");
        return Map.copyOf(maps);
    }
}
