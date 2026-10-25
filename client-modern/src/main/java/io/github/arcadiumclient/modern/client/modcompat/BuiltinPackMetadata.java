// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.modcompat;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.Set;

public final class BuiltinPackMetadata {
    private static final Set<String> PACKS = Set.of("betterstats", "tcdcommons");
    private BuiltinPackMetadata() { }
    public static JsonObject repair(String id, boolean fabricModPack, JsonObject metadata) {
        if (!fabricModPack || !PACKS.contains(id) || !metadata.has("pack") || !metadata.get("pack").isJsonObject()) return metadata;
        var pack = metadata.getAsJsonObject("pack");
        if (pack.has("min_format") || pack.has("max_format") || !pack.has("pack_format") || !pack.get("pack_format").isJsonPrimitive()) return metadata;
        var format = pack.getAsJsonPrimitive("pack_format");
        if (!format.isNumber() || format.getAsBigDecimal().compareTo(BigDecimal.valueOf(75)) != 0) return metadata;
        var result = metadata.deepCopy();
        result.getAsJsonObject("pack").addProperty("min_format", 75);
        result.getAsJsonObject("pack").addProperty("max_format", 75);
        return result;
    }
}
