// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

public final class BedwarsStarFormatter {
    private static final String[] RAINBOW = {"§c", "§6", "§e", "§a", "§b", "§d", "§5"};
    private static final char STAR = '\u272B';

    private BedwarsStarFormatter() {
    }

    public static String formatBracketed(int star) {
        if (star < 0) {
            star = 0;
        }
        if (star >= 1000) {
            return formatRainbow(star);
        }
        String color = prestigeColor(star);
        return color + "[" + star + STAR + "]";
    }

    public static String formatColoredStarValue(int star) {
        if (star < 0) {
            star = 0;
        }
        if (star >= 1000) {
            return formatRainbowDigits(star) + "§r";
        }
        return prestigeColor(star) + star;
    }

    private static String formatRainbow(int star) {
        String digits = Integer.toString(star);
        StringBuilder builder = new StringBuilder();
        builder.append(RAINBOW[RAINBOW.length - 1]).append('[');
        for (int i = 0; i < digits.length(); ++i) {
            builder.append(RAINBOW[i % RAINBOW.length]).append(digits.charAt(i));
        }
        builder.append(RAINBOW[digits.length() % RAINBOW.length]).append(STAR);
        builder.append(RAINBOW[RAINBOW.length - 1]).append(']');
        return builder.toString();
    }

    private static String formatRainbowDigits(int star) {
        String digits = Integer.toString(star);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < digits.length(); ++i) {
            builder.append(RAINBOW[i % RAINBOW.length]).append(digits.charAt(i));
        }
        return builder.toString();
    }

    public static String prestigeColor(int star) {
        int prestige = star / 100;
        switch (prestige) {
            case 0:
                return "§7";
            case 1:
                return "§f";
            case 2:
                return "§6";
            case 3:
                return "§b";
            case 4:
                return "§2";
            case 5:
                return "§3";
            case 6:
                return "§4";
            case 7:
                return "§d";
            case 8:
                return "§9";
            case 9:
                return "§5";
            default:
                return "§c";
        }
    }
}
