package dev.microcontrollers.betternightvision.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class BetterNightVisionConfig extends Config {
    public static final BetterNightVisionConfig INSTANCE = new BetterNightVisionConfig();

    @Switch(
            title = "better-night-vision.disable-night-vision",
            description = "better-night-vision.disable-night-vision.description"
    )
    public static boolean disableNightVision = false;

    @Switch(
            title = "better-night-vision.cleaner-night-vision-decay",
            description = "better-night-vision.cleaner-night-vision-decay.description"
    )
    public static boolean cleanerNightVision = true;

    public BetterNightVisionConfig() {
        super("betternightvision.json", "/assets/betternightvision/icon.png", "Better Night Vision", Category.QOL);
        save();

        addDependency("cleanerNightVision", null, () -> disableNightVision ? Property.Display.DISABLED : Property.Display.SHOWN);
    }
}
