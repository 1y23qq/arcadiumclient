package dev.microcontrollers.betterscreens.config;

import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class BetterScreensConfig extends Config {
    public static BetterScreensConfig INSTANCE = new BetterScreensConfig();

    @Slider(
            title = "betterscreens.container-scale.name",
            description = "betterscreens.container-scale.description",
            category = "betterscreens.functionality",
            min = -1,
            max = 6
    )
    public static int containerScale = -1;

    @Switch(
            title = "betterscreens.prevent-closing-screens.name",
            description = "betterscreens.prevent-closing-screens.description",
            category = "betterscreens.functionality"
    )
    public static boolean preventClosingScreens = false;

    @Switch(
            title = "betterscreens.dont-reset-cursor.name",
            description = "betterscreens.dont-reset-cursor.description",
            category = "betterscreens.functionality"
    )
    public static boolean dontResetCursor = false;

    @Slider(
            title = "betterscreens.cursor-reset-timer.name",
            description = "betterscreens.cursor-reset-timer.description",
            category = "betterscreens.functionality",
            max = 5000F
    )
    public static long cursorResetTimer = 150L;

    @Switch(
            title = "betterscreens.remove-status-effect-shift.name",
            description = "betterscreens.remove-status-effect-shift.description",
            category = "betterscreens.functionality"
    )
    public static boolean removeStatusEffectsShift = true;

    @Switch(
            title = "betterscreens.click-out-of-containers.name",
            description = "betterscreens.click-out-of-containers.description",
            category = "betterscreens.functionality"
    )
    public static boolean clickOutOfContainers = false;

    @Switch(
            title = "betterscreens.replace-open-to-lan.name",
            description = "betterscreens.replace-open-to-lan.description",
            category = "betterscreens.functionality"
    )
    public static boolean replaceOpenToLan = true;

    @Switch(
            title = "betterscreens.show-server-preview.name",
            description = "betterscreens.show-server-preview.description",
            category = "betterscreens.functionality"
    )
    public static boolean showServerPreview = true;

    @Include public static String lastServerIP = "";

    @Slider(
            title = "betterscreens.container-background-opacity.name",
            description = "betterscreens.container-background-opacity.description",
            category = "betterscreens.visual"
    )
    public static float containerBackgroundOpacity = (208 / 255F) * 100F;

    @Slider(
            title = "betterscreens.container-texture-opacity.name",
            description = "betterscreens.container-texture-opacity.description",
            category = "betterscreens.visual"
    )
    public static float containerTextureOpacity = 100F;

    @Color(
            title = "betterscreens.container-label-color.name",
            description = "betterscreens.container-label-color.description",
            category = "betterscreens.visual"
    )
    public static PolyColor containerLabelColor = new PolyColor(4210752);

    @Color(
            title = "betterscreens.player-inventory-label-color.name",
            description = "betterscreens.player-inventory-label-color.description",
            category = "betterscreens.visual"
    )
    public static PolyColor playerInventoryLabelColor = new PolyColor(4210752);

    public BetterScreensConfig() {
        super("betterscreens.json", "/assets/betterscreens/icon.png", "Better Screens", Config.Category.QOL);
        save();
    }
}