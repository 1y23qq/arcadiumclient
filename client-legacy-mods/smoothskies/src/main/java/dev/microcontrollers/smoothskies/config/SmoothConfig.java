package dev.microcontrollers.smoothskies.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class SmoothConfig extends Config {
    public static final SmoothConfig INSTANCE = new SmoothConfig();

    @Switch(
            title = "smoothskies.lower-sky-void-darkness.name",
            description = "smoothskies.lower-sky-void-darkness.description"
    )
    public static boolean lowerSkyVoidDarkness = true;

    @Switch(
            title = "smoothskies.remove-void-box.name",
            description = "smoothskies.remove-void-box.description"
    )
    public static boolean removeVoidBox = true;

    @Switch(
            title = "smoothskies.remove-sunrise-and-sunset-coloring.name",
            description = "smoothskies.remove-sunrise-and-sunset-coloring.description",
            subcategory = "smoothskies.extras"
    )
    public static boolean removeSunriseAndSunsetColoring = false;

    @Switch(
            title = "smoothskies.remove-sky-in-void.name",
            description = "smoothskies.remove-sky-in-void.description",
            subcategory = "smoothskies.extras"
    )
    public static boolean removeSkyInVoid = false;

    public SmoothConfig() {
        super("smoothskies.json", "/assets/smoothskies/icon.png", "Smooth Skies", Category.QOL);
        save();
    }
}
