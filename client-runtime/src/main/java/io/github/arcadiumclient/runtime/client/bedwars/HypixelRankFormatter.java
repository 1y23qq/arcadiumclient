// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

public final class HypixelRankFormatter {
    private HypixelRankFormatter() {
    }

    public static String formatRankPrefix(BedwarsStats stats) {
        if (stats == null || stats.isNicked()) {
            return "";
        }
        if (stats.getPrefix() != null && !stats.getPrefix().isEmpty()) {
            return stats.getPrefix().replace('&', '\u00a7') + " ";
        }

        String monthly = normalize(stats.getMonthlyPackageRank());
        String packageRank = firstNonNone(normalize(stats.getNewPackageRank()), normalize(stats.getPackageRank()));
        String plusColor = colorFromName(stats.getRankPlusColor(), "§c");
        String monthlyColor = colorFromName(stats.getMonthlyRankColor(), "§6");

        if ("SUPERSTAR".equals(monthly) || "MVP_PLUS_PLUS".equals(monthly)) {
            return monthlyColor + "[MVP" + plusColor + "++" + monthlyColor + "] ";
        }
        if ("MVP_PLUS".equals(packageRank)) {
            return "§b[MVP" + plusColor + "+" + "§b] ";
        }
        if ("MVP".equals(packageRank)) {
            return "§b[MVP] ";
        }
        if ("VIP_PLUS".equals(packageRank)) {
            return "§a[VIP§6+§a] ";
        }
        if ("VIP".equals(packageRank)) {
            return "§a[VIP] ";
        }
        if ("YOUTUBER".equals(packageRank)) {
            return "§c[§fYOUTUBE§c] ";
        }
        return "";
    }

    public static String nameColor(BedwarsStats stats) {
        if (stats == null || stats.isNicked()) {
            return "§7";
        }
        String monthly = normalize(stats.getMonthlyPackageRank());
        String packageRank = firstNonNone(normalize(stats.getNewPackageRank()), normalize(stats.getPackageRank()));
        if ("SUPERSTAR".equals(monthly) || "MVP_PLUS_PLUS".equals(monthly)) {
            return colorFromName(stats.getMonthlyRankColor(), "§6");
        }
        if ("MVP_PLUS".equals(packageRank) || "MVP".equals(packageRank)) {
            return "§b";
        }
        if ("VIP_PLUS".equals(packageRank) || "VIP".equals(packageRank)) {
            return "§a";
        }
        if ("YOUTUBER".equals(packageRank)) {
            return "§c";
        }
        return "§7";
    }

    private static String firstNonNone(String first, String second) {
        if (first != null && !first.isEmpty() && !"NONE".equals(first)) {
            return first;
        }
        if (second != null && !second.isEmpty() && !"NONE".equals(second)) {
            return second;
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static String colorFromName(String colorName, String fallback) {
        if (colorName == null || colorName.isEmpty()) {
            return fallback;
        }
        switch (colorName.toUpperCase()) {
            case "BLACK":
                return "§0";
            case "DARK_BLUE":
                return "§1";
            case "DARK_GREEN":
                return "§2";
            case "DARK_AQUA":
                return "§3";
            case "DARK_RED":
                return "§4";
            case "DARK_PURPLE":
                return "§5";
            case "GOLD":
                return "§6";
            case "GRAY":
                return "§7";
            case "DARK_GRAY":
                return "§8";
            case "BLUE":
                return "§9";
            case "GREEN":
                return "§a";
            case "AQUA":
                return "§b";
            case "RED":
                return "§c";
            case "LIGHT_PURPLE":
                return "§d";
            case "YELLOW":
                return "§e";
            case "WHITE":
                return "§f";
            default:
                return fallback;
        }
    }
}
