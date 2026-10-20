package org.codeberg.chromatic.countdown.util;

import org.codeberg.chromatic.countdown.config.TntCountdownConfig;

import java.awt.*;

public class CountdownFormat {

    //? if <= 1.8.9
    public static int nametagColor;

    public static String text(float remainingTicks) {
        float displayValue = switch (TntCountdownConfig.timeDisplayUnits) {
            case 0 -> remainingTicks * 50f;
            case 1 -> remainingTicks;
            default -> remainingTicks / 20f;
        };

        return TntCountdownConfig.countdownTextFormat.replace(
                "%VALUE%",
                String.format("%." + TntCountdownConfig.decimalPrecision + "f", displayValue)
        );
    }

    public static int color(float remainingTicks) {
        float p = Math.clamp(1f - remainingTicks / 80f, 0f, 1f);

        return new Color(
                lerp(p, TntCountdownConfig.startColor.getRed(),   TntCountdownConfig.endColor.getRed()),
                lerp(p, TntCountdownConfig.startColor.getGreen(), TntCountdownConfig.endColor.getGreen()),
                lerp(p, TntCountdownConfig.startColor.getBlue(),  TntCountdownConfig.endColor.getBlue())
        ).getRGB();
    }

    private static int lerp(float p, int start, int end) {
        return Math.round(start + p * (end - start));
    }

}
