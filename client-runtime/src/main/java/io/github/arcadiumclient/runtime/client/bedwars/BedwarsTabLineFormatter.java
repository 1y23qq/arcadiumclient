// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

public final class BedwarsTabLineFormatter {
    private BedwarsTabLineFormatter() {
    }

    public static String format(BedwarsStats stats, String fallbackName, String tabNameColor) {
        String name = fallbackName != null && !fallbackName.isEmpty() ? fallbackName : "?";
        String nameColor = tabNameColor != null && !tabNameColor.isEmpty()
                ? tabNameColor
                : HypixelRankFormatter.nameColor(stats);
        if (stats == null || stats.isNicked()) {
            return nameColor + name + " §7: §cNicked";
        }
        String star = BedwarsStarFormatter.formatBracketed(stats.getStar());
        String rank = HypixelRankFormatter.formatRankPrefix(stats);
        String fkdr = FkdrColorFormatter.format(stats.getFkdr());
        return star + " " + rank + nameColor + name + " §7: " + fkdr;
    }

    public static String resolveTabNameColor(String vanillaFormatted, String username, String teamPrefix) {
        ColorAtName match = colorAtName(vanillaFormatted, username);
        if (match.nameFound) {
            if (match.color != null) {
                return match.color;
            }
        } else {
            String last = lastColorCode(vanillaFormatted);
            if (last != null) {
                return last;
            }
        }
        return lastColorCode(teamPrefix);
    }

    private static ColorAtName colorAtName(String formatted, String username) {
        if (formatted == null || username == null || username.isEmpty()) {
            return ColorAtName.notFound();
        }
        String lastColor = null;
        int namePos = 0;
        for (int i = 0; i < formatted.length(); ++i) {
            char c = formatted.charAt(i);
            if ((c == '\u00a7' || c == '&') && i + 1 < formatted.length()) {
                char code = Character.toLowerCase(formatted.charAt(i + 1));
                if (isColorDigit(code)) {
                    lastColor = "§" + code;
                } else if (code == 'r') {
                    lastColor = null;
                }
                ++i;
                namePos = 0;
                continue;
            }
            char expected = username.charAt(namePos);
            if (c == expected) {
                ++namePos;
                if (namePos == username.length()) {
                    return ColorAtName.found(lastColor);
                }
            } else {
                namePos = c == username.charAt(0) ? 1 : 0;
            }
        }
        return ColorAtName.notFound();
    }

    private static String lastColorCode(String formatted) {
        if (formatted == null || formatted.isEmpty()) {
            return null;
        }
        String last = null;
        for (int i = 0; i < formatted.length() - 1; ++i) {
            char c = formatted.charAt(i);
            if (c != '\u00a7' && c != '&') {
                continue;
            }
            char code = Character.toLowerCase(formatted.charAt(i + 1));
            if (isColorDigit(code)) {
                last = "§" + code;
            } else if (code == 'r') {
                last = null;
            }
            ++i;
        }
        return last;
    }

    private static boolean isColorDigit(char code) {
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f';
    }

    private static final class ColorAtName {
        final boolean nameFound;
        final String color;

        private ColorAtName(boolean nameFound, String color) {
            this.nameFound = nameFound;
            this.color = color;
        }

        static ColorAtName found(String color) {
            return new ColorAtName(true, color);
        }

        static ColorAtName notFound() {
            return new ColorAtName(false, null);
        }
    }
}
