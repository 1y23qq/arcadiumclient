package org.codeberg.chromatic.countdown.config;

import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;

public class TntCountdownConfig extends Config {

    @Dropdown(
            title = "Time Display Units",
            options = { "Milliseconds", "Ticks", "Seconds" },
            description = "Determines whether the countdown is shown in milliseconds, ticks, or seconds."
    )
    public static int timeDisplayUnits = 2;

    @Text(
            title = "Countdown Text Format",
            description = "Controls how the countdown text is displayed. Use '%VALUE%' to insert the remaining time."
    )
    public static String countdownTextFormat = "%VALUE%";

    @Slider(
            title = "Decimal Precision",
            description = "Sets how many decimal places are shown when displaying the countdown value.",
            max = 3.0f, step = 1f
    )
    public static int decimalPrecision = 2;

    @Color(
            title = "Start Color",
            description = "The color used when the countdown begins.",
            alpha = false, subcategory = "Color"
    )
    public static PolyColor startColor = new PolyColor(0xFF00FF00);

    @Color(
            title = "End Color",
            description = "The color the countdown gradually fades into as it approaches zero.",
            alpha = false, subcategory = "Color"
    )
    public static PolyColor endColor = new PolyColor(0xFFFF0000);

    public TntCountdownConfig() {
        super("countdown.json", "TNTCountdown", Config.Category.QOL);
    }
}
