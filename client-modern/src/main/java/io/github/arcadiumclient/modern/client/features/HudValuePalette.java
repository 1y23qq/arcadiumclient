// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class HudValuePalette {
    public static final int PING_BEST = 0xFF55FF55;
    public static final int PING_GOOD = 0xFF00AA00;
    public static final int PING_FAIR = 0xFFFFFF55;
    public static final int PING_HIGH = 0xFFFFAA00;
    public static final int PING_POOR = 0xFFFF5555;
    public static final int PING_WORST = 0xFFAA0000;

    private HudValuePalette() {}

    public static int pingRange(long milliseconds) {
        if (milliseconds < 0) return -1;
        if (milliseconds < 75) return 0;
        if (milliseconds < 145) return 1;
        if (milliseconds < 200) return 2;
        if (milliseconds < 300) return 3;
        if (milliseconds < 400) return 4;
        return 5;
    }

    public static int tpsRange(float value) {
        if (!Float.isFinite(value)) return 2;
        BigDecimal displayed = roundedTps(value);
        if (displayed.compareTo(new BigDecimal("19.5")) >= 0 &&
                displayed.compareTo(new BigDecimal("20.5")) <= 0) return 0;
        return displayed.compareTo(new BigDecimal("15.0")) > 0 &&
                displayed.compareTo(new BigDecimal("19.5")) < 0 ? 1 : 2;
    }

    public static String tpsText(float value) {
        return Float.isFinite(value) ? roundedTps(value).toPlainString() : "Unknown";
    }

    private static BigDecimal roundedTps(float value) {
        return new BigDecimal(Float.toString(value)).setScale(1, RoundingMode.HALF_UP);
    }

    public static int readableBiomeColor(int rgb) {
        int red = rgb >> 16 & 255;
        int green = rgb >> 8 & 255;
        int blue = rgb & 255;
        while (luminance(red, green, blue) < 0.18) {
            red = Math.min(255, red + Math.max(1, (255 - red) / 16));
            green = Math.min(255, green + Math.max(1, (255 - green) / 16));
            blue = Math.min(255, blue + Math.max(1, (255 - blue) / 16));
        }
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static double luminance(int red, int green, int blue) {
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
