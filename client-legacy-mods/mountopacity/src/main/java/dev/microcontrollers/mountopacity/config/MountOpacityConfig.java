package dev.microcontrollers.mountopacity.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class MountOpacityConfig extends Config {
    public static final MountOpacityConfig INSTANCE = new MountOpacityConfig();

    @Slider(
            title = "mount-opacity.horse-opacity",
            description = "mount-opacity.horse-opacity.description"
    )
    public static float horseOpacity = 100F;

    @Slider(
            title = "mount-opacity.pig-opacity",
            description = "mount-opacity.pig-opacity.description"
    )
    public static float pigOpacity = 100F;

    @SuppressWarnings("unused")
    @Slider(
            title = "mount-opacity.llama-opacity",
            description = "mount-opacity.llama-opacity.description"
    )
    public static float llamaOpacity = 100F;

    @SuppressWarnings("unused")
    @Slider(
            title = "mount-opacity.strider-opacity",
            description = "mount-opacity.strider-opacity.description"
    )
    public static float striderOpacity = 100F;

    @SuppressWarnings("unused")
    @Slider(
            title = "mount-opacity.camel-opacity",
            description = "mount-opacity.camel-opacity.description"
    )
    public static float camelOpacity = 100F;

    @SuppressWarnings("unused")
    @Slider(
            title = "mount-opacity.nautilus-opacity",
            description = "mount-opacity.nautilus-opacity.description",
            category = "mount-opacity.general"
    )
    public static float nautilusOpacity = 100F;

    @SuppressWarnings("unused")
    @Slider(
            title = "mount-opacity.happy-ghast-opacity",
            description = "mount-opacity.happy-ghast-opacity.description",
            category = "mount-opacity.general"
    )
    public static float happyGhastOpacity = 100F;


    @Slider(
            title = "mount-opacity.default-opacity",
            description = "mount-opacity.default-opacity.description"
    )
    public static float defaultOpacity = 100F;

    @Switch(
            title = "mount-opacity.only-in-first-person",
            description = "mount-opacity.only-in-first-person.description",
            category = "mount-opacity.customization"
    )
    public static boolean onlyInFirstPerson = false;

    @Switch(
            title = "mount-opacity.use-look-direction",
            description = "mount-opacity.use-look-direction.description",
            category = "mount-opacity.customization",
            subcategory = "mount-opacity.look-direction"
    )
    public static boolean useLookDirection = false;

    @Slider(
            title = "mount-opacity.look-offset",
            description = "mount-opacity.look-offset.description",
            category = "mount-opacity.customization",
            subcategory = "mount-opacity.look-direction",
            min = -50F,
            max = 50F
    )
    public static float lookOffset = 20F;

    @Slider(
            title = "mount-opacity.look-range",
            description = "mount-opacity.look-range.description",
            category = "mount-opacity.customization",
            subcategory = "mount-opacity.look-direction",
            max = 90F
    )
    public static float lookRange = 80F;

    public MountOpacityConfig() {
        super("mountopacity.json", "/assets/mountopacity/icon.png", "Mount Opacity", Category.QOL);
        save();

        hideIf("llamaOpacity", () -> true);
        hideIf("striderOpacity", () -> true);
        hideIf("camelOpacity", () -> true);
        hideIf("nautilusOpacity", () -> true);
        hideIf("happyGhastOpacity", () -> true);
    }
}
