// SPDX-License-Identifier: GPL-3.0-only
// VignetteTweaks configuration compatibility: chromatic and awruff.
package io.github.arcadiumclient.modern.client.features;

import net.minecraft.client.Minecraft;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Accordion;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.DependsOn;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public final class VignetteConfig extends Config {
    public static final VignetteConfig INSTANCE = new VignetteConfig();
    @Switch(title = "Enabled") public static boolean enabled = true;
    @Dropdown(title = "Type", options = {"Static", "Amplified"}) public static int type = 1;
    @Slider(title = "Strength", min = 0, max = 100, step = 1) public static int strength = 50;
    @Slider(title = "Minimum Strength", min = 0, max = 100, step = 1) public static float minimumStrength = 0;
    @Slider(title = "Maximum Strength", min = 0, max = 100, step = 1) public static float maximumStrength = 100;
    @Slider(title = "Strength Multiplier", min = 0, max = 5, step = 0.05f) public static float strengthMultiplier = 1;

    private VignetteConfig() {
        super("vignettetweaks.json", "Vignette Tweaks", Category.VISUALS);
        hideIf("strength", () -> type == 1);
        for (String key : new String[]{"minimumStrength", "maximumStrength", "strengthMultiplier"}) hideIf(key, () -> type != 1);
        disableAllUnless("enabled");
    }
    public static float strength(float original) { return enabled ? VignettePolicy.strength(type, original, strength, minimumStrength, maximumStrength, strengthMultiplier) : original; }
    public static int tint(int original) {
        var player = Minecraft.getInstance().player;
        if (!enabled || player == null || !Health.enabled && !Hunger.enabled && !Air.enabled) return original;
        float[] values = {player.getHealth() / Math.max(1, player.getMaxHealth()) * 100, player.getFoodData().getFoodLevel() * 5f, player.getAirSupply() / (float) Math.max(1, player.getMaxAirSupply()) * 100};
        float best = 0;
        PolyColor color = null;
        if (Health.enabled) { float value = VignettePolicy.condition(values[0], Health.threshold, Health.minimumStrength, Health.maximumStrength); if (value > best) { best = value; color = Health.color; } }
        if (Hunger.enabled) { float value = VignettePolicy.condition(values[1], Hunger.threshold, Hunger.minimumStrength, Hunger.maximumStrength); if (value > best) { best = value; color = Hunger.color; } }
        if (Air.enabled) { float value = VignettePolicy.condition(values[2], Air.threshold, Air.minimumStrength, Air.maximumStrength); if (value > best) { best = value; color = Air.color; } }
        return color == null ? original : VignettePolicy.tint(original, color.getArgb(), best);
    }

    @Accordion(title = "Health", subcategory = "Status") public static class Health {
        @Include public static boolean enabled = false;
        @DependsOn("Health.enabled") @Color(title = "Color", alpha = false) public static PolyColor color = new PolyColor(-4517101);
        @DependsOn("Health.enabled") @Slider(title = "Threshold", min = 0, max = 100, step = 1) public static int threshold = 50;
        @DependsOn("Health.enabled") @Slider(title = "Minimum Strength", min = 0, max = 100, step = 1) public static int minimumStrength = 0;
        @DependsOn("Health.enabled") @Slider(title = "Maximum Strength", min = 0, max = 100, step = 1) public static int maximumStrength = 100;
    }
    @Accordion(title = "Hunger", subcategory = "Status") public static class Hunger {
        @Include public static boolean enabled = false;
        @DependsOn("Hunger.enabled") @Color(title = "Color", alpha = false) public static PolyColor color = new PolyColor(-10979757);
        @DependsOn("Hunger.enabled") @Slider(title = "Threshold", min = 0, max = 100, step = 1) public static int threshold = 50;
        @DependsOn("Hunger.enabled") @Slider(title = "Minimum Strength", min = 0, max = 100, step = 1) public static int minimumStrength = 0;
        @DependsOn("Hunger.enabled") @Slider(title = "Maximum Strength", min = 0, max = 100, step = 1) public static int maximumStrength = 100;
    }
    @Accordion(title = "Air", subcategory = "Status") public static class Air {
        @Include public static boolean enabled = false;
        @DependsOn("Air.enabled") @Color(title = "Color", alpha = false) public static PolyColor color = new PolyColor(-15454882);
        @DependsOn("Air.enabled") @Slider(title = "Threshold", min = 0, max = 100, step = 1) public static int threshold = 50;
        @DependsOn("Air.enabled") @Slider(title = "Minimum Strength", min = 0, max = 100, step = 1) public static int minimumStrength = 0;
        @DependsOn("Air.enabled") @Slider(title = "Maximum Strength", min = 0, max = 100, step = 1) public static int maximumStrength = 100;
    }
}
