// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.util.Locale;
import java.util.regex.Pattern;

public final class BedwarsTabLineFormatter {
    private static final Pattern TOKENS = Pattern.compile("%([A-Z_]+)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_CODES = Pattern.compile("(?i)[§&]([0-9a-fk-or])");
    private BedwarsTabLineFormatter() {
    }

    public static String format(BedwarsStats stats, String fallbackName, String tabNameColor) {
        return format(stats, fallbackName, tabNameColor, true, 0, null);
    }

    public static String format(BedwarsStats stats, String fallbackName, String tabNameColor, boolean showRank, int metric, String template) {
        String name = fallbackName != null && !fallbackName.isEmpty() ? fallbackName : "?";
        String nameColor = tabNameColor != null && !tabNameColor.isEmpty()
                ? tabNameColor
                : HypixelRankFormatter.nameColor(stats);
        if (stats == null || stats.isNicked()) {
            return nameColor + name + " §7: §cNicked";
        }
        String star = BedwarsStarFormatter.formatBracketed(stats.getStar());
        String rank = showRank ? HypixelRankFormatter.formatRankPrefix(stats) : "";
        if (template != null && !template.isBlank()) {
            String input = template.length() > 512 ? template.substring(0, 512) : template;
            input = COLOR_CODES.matcher(input).replaceAll(match -> "§" + match.group(1).toLowerCase(Locale.ROOT));
            input = input.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
            var matcher = TOKENS.matcher(input);
            StringBuilder out = new StringBuilder();
            int end = 0;
            boolean userStyle = false;
            while (matcher.find()) {
                userStyle |= COLOR_CODES.matcher(input.substring(end, matcher.start())).find();
                String replacement = switch (matcher.group(1).toUpperCase(Locale.ROOT)) {
                    case "STAR", "STARS" -> star;
                    case "RANK" -> rank.stripTrailing();
                    case "USERNAME", "NAME" -> nameColor + name;
                    case "FKDR" -> value(stats, 0, rank);
                    case "KDR" -> value(stats, 1, rank);
                    case "WINS" -> value(stats, 2, rank);
                    case "WLR" -> value(stats, 3, rank);
                    case "LEVEL", "HYPIXEL_LEVEL" -> value(stats, 4, rank);
                    default -> matcher.group();
                };
                if (userStyle) replacement = COLOR_CODES.matcher(replacement).replaceAll("");
                matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement));
                end = matcher.end();
            }
            matcher.appendTail(out);
            return out.toString();
        }
        return star + " " + rank + nameColor + name + " §7: " + value(stats, metric, rank);
    }

    private static String value(BedwarsStats stats, int metric, String rank) {
        return switch (metric) {
            case 1 -> ratio(stats.getKdr());
            case 2 -> stats.getWins() < 0 ? "§7--" : "§f" + String.format(Locale.ROOT, "%,d", stats.getWins());
            case 3 -> ratio(stats.getWlr());
            case 4 -> stats.getNetworkLevel() < 0 ? "§7--" : "§f" + stats.getNetworkLevel();
            case 5 -> BedwarsStarFormatter.formatColoredStarValue(stats.getStar());
            case 6 -> rank.isEmpty() ? "§7--" : rank.stripTrailing();
            default -> FkdrColorFormatter.format(stats.getFkdr());
        };
    }

    private static String ratio(double value) {
        return Double.isFinite(value) ? "§f" + String.format(Locale.ROOT, "%.2f", value) : "§7--";
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
