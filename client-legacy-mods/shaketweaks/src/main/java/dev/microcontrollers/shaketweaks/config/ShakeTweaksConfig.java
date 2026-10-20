package dev.microcontrollers.shaketweaks.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class ShakeTweaksConfig extends Config {
    public static final ShakeTweaksConfig INSTANCE = new ShakeTweaksConfig();

    @Switch(
            title = "shake-tweaks.disable-screen-bobbing",
            description = "shake-tweaks.disable-screen-bobbing.description"
    )
    public static boolean disableScreenBobbing = false;

    @Switch(
            title = "shake-tweaks.disable-hand-bobbing",
            description = "shake-tweaks.disable-hand-bobbing.description"
    )
    public static boolean disableHandBobbing = false;

    @Switch(
            title = "shake-tweaks.disable-horizontal-bobbing",
            description = "shake-tweaks.disable-horizontal-bobbing.description"
    )
    public static boolean disableHorizontalBobbing = false;

    @Switch(
            title = "shake-tweaks.disable-map-bobbing",
            description = "shake-tweaks.disable-map-bobbing.description"
    )
    public static boolean disableMapBobbing = false;

    @Switch(
            title = "shake-tweaks.disable-hand-damage-tilt",
            description = "shake-tweaks.disable-hand-damage-tilt.description"
    )
    public static boolean disableHandDamage = false;

    @Switch(
            title = "shake-tweaks.disable-screen-damage-tilt",
            description = "shake-tweaks.disable-screen-damage-tilt.description"
    )
    public static boolean disableScreenDamage = false;

    @Switch(
            title = "shake-tweaks.disable-hand-view-sway",
            description = "shake-tweaks.disable-hand-view-sway.description"
    )
    public static boolean disableHandViewSway = false;

    public ShakeTweaksConfig() {
        super("shaketweaks.json", "/assets/shaketweaks/icon.png", "Shake Tweaks", Category.QOL);
        save();
    }
}