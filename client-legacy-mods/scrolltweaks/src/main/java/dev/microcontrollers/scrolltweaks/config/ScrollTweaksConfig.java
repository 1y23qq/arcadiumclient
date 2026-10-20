package dev.microcontrollers.scrolltweaks.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;


public class ScrollTweaksConfig extends Config {
    public static final ScrollTweaksConfig INSTANCE = new ScrollTweaksConfig();

    @Switch(
            title = "scroll-tweaks.disable-scrolling",
            description = "scroll-tweaks.disable-scrolling.description"
    )
    public static boolean disableScroll = false;

    @Switch(
            title = "scroll-tweaks.reverse-scroll-direction",
            description = "scroll-tweaks.reverse-scroll-direction.description"
    )
    public static boolean reverseScroll = false;

    @Switch(
            title = "scroll-tweaks.prevent-overflow-scrolling",
            description = "scroll-tweaks.prevent-overflow-scrolling.description"
    )
    public static boolean preventOverflowScroll = false;

    public ScrollTweaksConfig() {
        super("scrolltweaks.json", "/assets/scrolltweaks/icon.png", "Scroll Tweaks", Category.QOL);
        save();
    }

}