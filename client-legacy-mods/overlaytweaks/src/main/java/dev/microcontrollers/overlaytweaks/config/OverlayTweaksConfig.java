package dev.microcontrollers.overlaytweaks.config;

import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public class OverlayTweaksConfig extends Config {
    public static OverlayTweaksConfig INSTANCE = new OverlayTweaksConfig();

    @Switch(
            title = "overlay-tweaks.keep-hand-in-hidden-hud",
            description = "overlay-tweaks.keep-hand-in-hidden-hud.description",
            category = "overlay-tweaks.miscellaneous",
            subcategory = "overlay-tweaks.hand"
    )
    public static boolean keepHand = false;

    @Slider(
            title = "overlay-tweaks.hand-invisibility-opacity",
            description = "overlay-tweaks.hand-invisibility-opacity.description",
            category = "overlay-tweaks.miscellaneous",
            subcategory = "overlay-tweaks.hand"
    )
    public static float handInvisibilityOpacity = 0F;

    public static float elderGuardianOpacity = 100F;

    public static float elderGuardianScale = 1F;

    @Switch(
            title = "overlay-tweaks.remove-water-overlay",
            description = "overlay-tweaks.remove-water-overlay.description",
            category = "overlay-tweaks.hud",
            subcategory = "overlay-tweaks.liquid"
    )
    public static boolean removeWaterOverlay = false;

    @Switch(
            title = "overlay-tweaks.remove-fire-overlay-when-resistant",
            description = "overlay-tweaks.remove-fire-overlay-when-resistant.description",
            category = "overlay-tweaks.hud",
            subcategory = "overlay-tweaks.fire"
    )
    public static boolean removeFireOverlay = false;

    @Slider(
            title = "overlay-tweaks.fire-overlay-height",
            description = "overlay-tweaks.fire-overlay-height.description",
            category = "overlay-tweaks.hud",
            subcategory = "overlay-tweaks.fire",
            min = -0.5F,
            max = 0F,
            step = 0.01F
    )
    public static float fireOverlayHeight = 0F;

    @Slider(
            title = "overlay-tweaks.fire-overlay-opacity",
            description = "overlay-tweaks.fire-overlay-opacity.description",
            category = "overlay-tweaks.hud",
            subcategory = "overlay-tweaks.fire"
    )
    public static float customFireOverlayOpacity = 100F;

    @Slider(
            title = "overlay-tweaks.suffocation-overlay-brightness",
            description = "overlay-tweaks.suffocation-overlay-brightness.description",
            category = "overlay-tweaks.hud",
            subcategory = "overlay-tweaks.environmental"
    )
    public static float suffocationOverlayBrightness = 10F;

    @Slider(
            title = "overlay-tweaks.nether-portal-opacity",
            description = "overlay-tweaks.nether-portal-opacity.description",
            category = "overlay-tweaks.hud",
            subcategory = "overlay-tweaks.environmental"
    )
    public static float netherPortalOpacity = 100F;

    @Slider(
            title = "overlay-tweaks.equippable-overlay-opacity",
            description = "overlay-tweaks.equippable-overlay-opacity.description",
            category = "overlay-tweaks.items",
            subcategory = "overlay-tweaks.equippables"
    )
    public static float equipableOpacity = 100F;

    private OverlayTweaksConfig() {
        super("overlaytweaks.json", "/assets/overlaytweaks/icon.png", "Overlay Tweaks", Category.QOL);
    }
}
