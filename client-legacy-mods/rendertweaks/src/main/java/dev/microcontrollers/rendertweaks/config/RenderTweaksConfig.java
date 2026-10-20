package dev.microcontrollers.rendertweaks.config;

import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class RenderTweaksConfig extends Config {
    public static final RenderTweaksConfig INSTANCE = new RenderTweaksConfig();

    @Switch(
            title = "Disable Lightning",
            description = "Disables rendering of lightning.",
            category = "Lightning Bolt",
            subcategory = "Lightning Bolt"
    )
    public static boolean disableLightning = false;

    @Color(
            title = "Color",
            description = "Solid color to use for lightning bolts. Enable chroma in the color picker for a rainbow effect.",
            category = "Lightning Bolt",
            subcategory = "Lightning Bolt"
    )
    public static PolyColor lightningColor = new PolyColor(((int) (0.3F * 255.0F) << 24) | ((int) (0.45F * 255.0F) << 16) | ((int) (0.45F * 255.0F) << 8) | (int) (0.5F * 255.0F));

    @Switch(
            title = "Disable Fishing Line",
            description = "Disables rendering of the fishing rod line.",
            category = "Fishing Line",
            subcategory = "Fishing Line"
    )
    public static boolean disableFishingLine = false;

    @Slider(
            title = "Width Multiplier",
            description = "Change the size of the fishing line.",
            category = "Fishing Line",
            subcategory = "Fishing Line",
            min = 0.1F,
            max = 5.0F,
            step = 0.1F
    )
    public static float fishingLineWidthMultiplier = 1.0F;

    @Color(
            title = "Color",
            description = "Color of the fishing line.",
            category = "Fishing Line",
            subcategory = "Fishing Line"
    )
    public static PolyColor fishingLineColor = new PolyColor(-16777216);

    //? if >=1.8.9 {
    /*@Switch(
            title = "Disable Stationary Border",
            description = "Disables rendering of the world border when it is stationary.",
            category = "World Border",
            subcategory = "Stationary"
    )
    public static boolean disableWorldBorderStationary = false;

    @Color(
            title = "Color",
            description = "Color of the stationary world border.",
            category = "World Border",
            subcategory = "Stationary"
    )
    public static PolyColor worldBorderStationaryColor = new PolyColor(2138367 | 0xFF000000);

    @Switch(
            title = "Disable Shrinking Border",
            description = "Disables rendering of the world border when it is shrinking.",
            category = "World Border",
            subcategory = "Shrinking"
    )
    public static boolean disableWorldBorderShrinking = false;

    @Color(
            title = "Color",
            description = "Color of the shrinking world border.",
            category = "World Border",
            subcategory = "Shrinking"
    )
    public static PolyColor worldBorderShrinkingColor = new PolyColor(16724016 | 0xFF000000);

    @Switch(
            title = "Disable Growing Border",
            description = "Disables rendering of the world border when it is growing.",
            category = "World Border",
            subcategory = "Growing"
    )
    public static boolean disableWorldBorderGrowing = false;

    @Color(
            title = "Color",
            description = "Color of the growing world border.",
            category = "World Border",
            subcategory = "Growing"
    )
    public static PolyColor worldBorderGrowingColor = new PolyColor(4259712 | 0xFF000000);

    *///?}

    public RenderTweaksConfig() {
        super("rendertweaksv2.json", "/assets/rendertweaks/icon.png", "Render Tweaks", Category.QOL);
        save();
    }
}