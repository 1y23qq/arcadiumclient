// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.JsonObject;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.Visualizer;
import org.polyfrost.oneconfig.api.config.v1.backend.Backend;
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry;

public final class LegacySmoothSkyOptions {
    public record Selection(boolean removeVoidBox, boolean removeSunriseColor) {}
    private static volatile Selection selected = new Selection(false, false);
    private LegacySmoothSkyOptions() {}
    public static Selection parse(JsonObject raw) {
        return new Selection(bool(raw, "removeVoidBox"), bool(raw, "removeSunriseAndSunsetColoring"));
    }
    private static boolean bool(JsonObject raw, String key) {
        if (!raw.has(key)) return false;
        var value = raw.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid Smooth Skies setting: " + key);
        return value.getAsBoolean();
    }
    public static void apply(Selection value) { selected = value; }
    public static boolean removeVoidBox() { return selected.removeVoidBox; }
    public static boolean removeSunriseColor() { return selected.removeSunriseColor; }
    public static JsonObject encoded(Selection value) {
        var json = new JsonObject();
        json.addProperty("removeVoidBox", value.removeVoidBox);
        json.addProperty("removeSunriseAndSunsetColoring", value.removeSunriseColor);
        return json;
    }
    public static void attach(Tree tree) {
        if (!tree.getID().equals("smoothskies") || Boolean.TRUE.equals(tree.getMetadata(Backend.UI_PLACEHOLDER_METADATA)) || tree.map.isEmpty()) return;
        var voidBox = Properties.functional(() -> removeVoidBox(), value -> NativeYaclProfiles.saveSkyOptions(new Selection(value, selected.removeSunriseColor)),
            "arcadiumRemoveVoidBox", "Remove Void Darkness", "Hide the dark sky disc below the world's horizon.", Boolean.class);
        var sunrise = Properties.functional(() -> removeSunriseColor(), value -> NativeYaclProfiles.saveSkyOptions(new Selection(selected.removeVoidBox, value)),
            "arcadiumRemoveSunriseColor", "Remove Sunrise and Sunset Coloring", "Hide sunrise and sunset coloring while keeping the sun, moon and stars.", Boolean.class);
        for (var property : java.util.List.of(voidBox, sunrise)) {
            property.addMetadata("visualizer", Visualizer.SwitchVisualizer.class);
            property.addMetadata("category", "General");
            property.addMetadata("subcategory", "Sky");
            property.addMetadata("default", false);
            tree.put(property);
        }
        ConfigRegistry.INSTANCE.invalidate();
    }
}
