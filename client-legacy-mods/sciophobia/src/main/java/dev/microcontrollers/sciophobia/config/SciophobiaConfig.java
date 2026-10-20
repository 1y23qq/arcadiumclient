package dev.microcontrollers.sciophobia.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class SciophobiaConfig extends Config {
    public static final SciophobiaConfig INSTANCE = new SciophobiaConfig();

    @Switch(
            title = "sciophobia.hide-shadow",
            description = "sciophobia.hide-shadow.description"
    )
    public static boolean removeShadows = false;

    @Switch(
            title = "sciophobia.force-shadow",
            description = "sciophobia.force-shadow.description"
    )
    public static boolean forceShadows = false;

    @Switch(
            title = "sciophobia.alternate-shadow",
            description = "sciophobia.alternate-shadow.description"
    )
    public static boolean alternateShadows = false;

    @Switch(
            title = "sciophobia.full-shadow",
            description = "sciophobia.full-shadow.description"
    )
    public static boolean fullShadow = false;

    public SciophobiaConfig() {
        super("sciophobia.json", "/assets/sciophobia/icon.png", "Sciophobia", Category.QOL);
        save();
    }
}
