// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.fixes.ItemStackTheFlatteningFix;
import io.github.arcadiumclient.modern.client.features.LegacyItemQueries;

public final class LegacyYaclSchema {
    public record Spec(String modId, String configClass, String legacyFile) {}
    public static final List<Spec> SPECS = List.of(
        spec("betternightvision", "BetterNightVisionConfig", "betternightvision.json"),
        spec("smoothskies", "SmoothConfig", "smoothskies.json"),
        spec("droppeditemtweaks", "DroppedItemTweaksConfig", "droppeditemtweaksv2.json"),
        spec("mountopacity", "MountOpacityConfig", "mountopacity.json"),
        spec("rendertweaks", "RenderTweaksConfig", "rendertweaksv2.json"),
        spec("shaketweaks", "ShakeTweaksConfig", "shaketweaks.json"),
        spec("confirmdisconnect", "ConfirmDisconnectConfig", "confirmdisconnect.json"),
        spec("scrolltweaks", "ScrollTweaksConfig", "scrolltweaks.json"),
        spec("numericalenchantments", "NumericalEnchantmentsConfig", "numericalenchantments.json"),
        spec("sciophobia", "SciophobiaConfig", "sciophobia.json"),
        spec("betterscreens", "BetterScreensConfig", "betterscreens.json")
    );
    private static Spec spec(String mod, String type, String file) { return new Spec(mod, "dev.microcontrollers." + mod + ".config." + type, file); }
    public static Spec find(String className) { return SPECS.stream().filter(spec -> spec.configClass.equals(className)).findFirst().orElse(null); }
    private LegacyYaclSchema() {}

    public static JsonObject toNative(Spec spec, JsonObject raw, JsonObject nativeFile, JsonObject marker, JsonObject defaults) {
        JsonObject result = defaults.deepCopy();
        for (var entry : raw.entrySet()) if (defaults.has(entry.getKey())) result.add(entry.getKey(), entry.getValue().deepCopy());
        JsonObject previousLegacy = object(marker, "legacy"), applied = object(marker, "nativeApplied");
        for (var entry : applied.entrySet()) if (defaults.has(entry.getKey()) && unchangedSource(spec, entry.getKey(), raw, previousLegacy)) result.add(entry.getKey(), entry.getValue().deepCopy());
        JsonObject priorNative = object(marker, "nativeFile");
        for (var entry : nativeFile.entrySet()) if (defaults.has(entry.getKey()) &&
                (!marker.has("nativeFile") || !entry.getValue().equals(priorNative.get(entry.getKey()))))
            result.add(entry.getKey(), entry.getValue().deepCopy());
        for (String key : colorFields(spec)) {
            JsonElement value = raw.get(key);
            if (!defaults.has(key) || value == null || !isLegacyColor(value)) continue;
            if (nativeFile.has(key) && (!marker.has("nativeFile") || !nativeFile.get(key).equals(priorNative.get(key)))) continue;
            if (applied.has(key) && java.util.Objects.equals(value, previousLegacy.get(key))) continue;
            ColorValue color = color(value);
            result.addProperty(key, color.argb);
            String base = key.substring(0, key.length() - "Color".length());
            if (!spec.modId.equals("rendertweaks")) continue;
            float[] hsb = Color.RGBtoHSB(color.argb >>> 16 & 255, color.argb >>> 8 & 255, color.argb & 255, null);
            putSplit(result, raw, previousLegacy, nativeFile, priorNative, marker.has("nativeFile"), key, base + "Alpha", color.argb >>> 24);
            putSplit(result, raw, previousLegacy, nativeFile, priorNative, marker.has("nativeFile"), key, base + "Chroma", color.chroma);
            putSplit(result, raw, previousLegacy, nativeFile, priorNative, marker.has("nativeFile"), key, base + "Saturation", hsb[1]);
            putSplit(result, raw, previousLegacy, nativeFile, priorNative, marker.has("nativeFile"), key, base + "Brightness", hsb[2]);
            putSplit(result, raw, previousLegacy, nativeFile, priorNative, marker.has("nativeFile"), key, base + "Speed", color.speed * .072f);
        }
        if (spec.modId.equals("droppeditemtweaks") && raw.has("uhcItemListLegacy")) {
            boolean legacyChanged = marker.has("legacy") && !raw.get("uhcItemListLegacy").equals(previousLegacy.get("uhcItemListLegacy"));
            boolean nativeChanged = raw.has("uchItemList") && marker.has("legacy") && !raw.get("uchItemList").equals(previousLegacy.get("uchItemList")) ||
                nativeFile.has("uchItemList") && (!marker.has("nativeFile") || !nativeFile.get("uchItemList").equals(priorNative.get("uchItemList")));
            if (!result.has("uchItemList") || !raw.has("uchItemList") && !nativeFile.has("uchItemList") && !applied.has("uchItemList") || legacyChanged && !nativeChanged)
                result.add("uchItemList", itemList(raw.get("uhcItemListLegacy")));
        }
        return result;
    }
    private static boolean unchangedSource(Spec spec, String field, JsonObject current, JsonObject previous) {
        if (!java.util.Objects.equals(current.get(field), previous.get(field))) return false;
        if (spec.modId.equals("droppeditemtweaks") && field.equals("uchItemList"))
            return java.util.Objects.equals(current.get("uhcItemListLegacy"), previous.get("uhcItemListLegacy"));
        if (spec.modId.equals("rendertweaks")) for (String color : colorFields(spec)) {
            String base = color.substring(0, color.length() - "Color".length());
            if (field.startsWith(base)) return java.util.Objects.equals(current.get(color), previous.get(color));
        }
        return true;
    }

    private static void putSplit(JsonObject target, JsonObject raw, JsonObject previous, JsonObject nativeFile, JsonObject priorNative, boolean trackedNative, String colorKey, String name, Object value) {
        if (!target.has(name)) return;
        if (nativeFile.has(name) && (!trackedNative || !nativeFile.get(name).equals(priorNative.get(name)))) return;
        boolean colorChanged = !raw.get(colorKey).equals(previous.get(colorKey));
        boolean splitChanged = raw.has(name) && !raw.get(name).equals(previous.get(name));
        if (raw.has(name) && (!colorChanged || splitChanged) && !previous.isEmpty()) return;
        if (value instanceof Boolean b) target.addProperty(name, b);
        else target.addProperty(name, (Number) value);
    }

    public static JsonObject toLegacy(Spec spec, JsonObject previous, JsonObject encoded) {
        JsonObject result = previous.deepCopy();
        for (var entry : encoded.entrySet()) result.add(entry.getKey(), entry.getValue().deepCopy());
        for (String key : colorFields(spec)) {
            if (!encoded.has(key)) continue;
            int argb = encoded.get(key).getAsInt();
            boolean chroma = false; float speed = 1;
            if (spec.modId.equals("rendertweaks")) {
                String base = key.substring(0, key.length() - "Color".length());
                chroma = encoded.has(base + "Chroma") && encoded.get(base + "Chroma").getAsBoolean();
                if (encoded.has(base + "Speed")) speed = encoded.get(base + "Speed").getAsFloat() / .072f;
                if (chroma) {
                    float[] hsb = Color.RGBtoHSB(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, null);
                    float saturation = encoded.has(base + "Saturation") ? encoded.get(base + "Saturation").getAsFloat() : hsb[1];
                    float brightness = encoded.has(base + "Brightness") ? encoded.get(base + "Brightness").getAsFloat() : hsb[2];
                    int alpha = encoded.has(base + "Alpha") ? Math.clamp(Math.round(encoded.get(base + "Alpha").getAsFloat()), 0, 255) : argb >>> 24;
                    argb = Color.HSBtoRGB(hsb[0], saturation, brightness) & 0xFFFFFF | alpha << 24;
                }
            }
            result.add(key, mergeColor(previous.get(key), colorJson(new ColorValue(argb, chroma, speed))));
        }
        return result;
    }

    private static List<String> colorFields(Spec spec) {
        return switch (spec.modId) {
            case "rendertweaks" -> List.of("lightningColor", "fishingLineColor", "worldBorderStationaryColor", "worldBorderShrinkingColor", "worldBorderGrowingColor");
            case "betterscreens" -> List.of("containerLabelColor", "playerInventoryLabelColor");
            default -> List.of();
        };
    }
    private static boolean isLegacyColor(JsonElement value) { return value.isJsonObject() || value.isJsonArray(); }
    private record ColorValue(int argb, boolean chroma, float speed) {}
    private static ColorValue color(JsonElement value) {
        boolean chroma = false; float speed = 1;
        if (value.isJsonObject() && value.getAsJsonObject().has("value")) value = value.getAsJsonObject().get("value");
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            chroma = object.has("chroma") && object.get("chroma").getAsBoolean();
            speed = object.has("chromaSpeed") ? object.get("chromaSpeed").getAsFloat() : 1;
            value = object.get("rgba");
        }
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return new ColorValue(value.getAsInt(), chroma, speed);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 4 || !Float.isFinite(speed))
            throw new IllegalArgumentException("Invalid legacy PolyColor value");
        JsonArray rgba = value.getAsJsonArray();
        int[] parts = new int[4];
        for (int i = 0; i < 4; i++) { parts[i] = rgba.get(i).getAsInt(); if (parts[i] < 0 || parts[i] > 255) throw new IllegalArgumentException("Invalid legacy color channel"); }
        return new ColorValue(parts[3] << 24 | parts[0] << 16 | parts[1] << 8 | parts[2], chroma, speed);
    }
    private static JsonObject colorJson(ColorValue color) {
        JsonArray rgba = new JsonArray();
        rgba.add(color.argb >>> 16 & 255); rgba.add(color.argb >>> 8 & 255); rgba.add(color.argb & 255); rgba.add(color.argb >>> 24);
        JsonElement value = rgba;
        if (color.chroma) { JsonObject mode = new JsonObject(); mode.add("rgba", rgba); mode.addProperty("chroma", true); mode.addProperty("chromaSpeed", color.speed); value = mode; }
        JsonObject result = new JsonObject(); result.addProperty("class", "org.polyfrost.compose.render.PolyColor"); result.add("value", value); return result;
    }
    private static JsonObject mergeColor(JsonElement previous, JsonObject encoded) {
        if (previous == null || !previous.isJsonObject()) return encoded;
        JsonObject result = previous.getAsJsonObject().deepCopy();
        if (result.has("value") && result.get("value").isJsonObject()) {
            JsonObject value = result.getAsJsonObject("value");
            JsonElement next = encoded.get("value");
            if (next.isJsonArray()) { value.add("rgba", next); value.addProperty("chroma", false); }
            else next.getAsJsonObject().entrySet().forEach(entry -> value.add(entry.getKey(), entry.getValue()));
            encoded.add("value", value);
        }
        encoded.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        return result;
    }
    private static JsonArray itemList(JsonElement value) {
        if (!value.isJsonArray()) throw new IllegalArgumentException("Invalid legacy dropped-item list");
        var names = new java.util.LinkedHashSet<String>();
        for (JsonElement entry : value.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid legacy item identifier");
            String name = entry.getAsString();
            boolean mapped = false;
            for (int metadata = 0; metadata < 16; metadata++) {
                String next = ItemStackTheFlatteningFix.updateItem(name, metadata);
                if (next == null) continue;
                Identifier id = Identifier.tryParse(next);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) { names.add(next); mapped = true; }
            }
            if (!mapped) {
                var family = LegacyItemQueries.missing(name);
                if (!family.isEmpty()) family.items().forEach(item -> names.add(BuiltInRegistries.ITEM.getKey(item).toString()));
                else { Identifier id = Identifier.tryParse(name); if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unavailable dropped-item identifier: " + name); names.add(name); }
            }
        }
        JsonArray result = new JsonArray(); names.forEach(result::add); return result;
    }
    private static JsonObject object(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject(); }
}
