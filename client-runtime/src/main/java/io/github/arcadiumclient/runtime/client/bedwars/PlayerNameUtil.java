// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PlayerNameUtil {
    private static final Pattern COLORS = Pattern.compile("§.");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private PlayerNameUtil() { }

    public static String plain(String text) { return text == null ? "" : COLORS.matcher(text).replaceAll("").trim(); }
    public static boolean valid(String name) { return name != null && USERNAME.matcher(name).matches(); }
    public static boolean sameAccountName(String a, String b) { return a != null && b != null && a.equalsIgnoreCase(b); }

    public static List<String> entries(String text) {
        List<String> result = new ArrayList<>();
        if (text != null) for (String entry : text.split("[,;\\r\\n]+")) {
            String value = entry.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
            if (result.size() >= 256) break;
        }
        return result;
    }

    public static boolean mapMatches(String list, String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String entry : entries(list)) {
            StringBuilder pattern = new StringBuilder();
            String[] parts = entry.split("\\*", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) pattern.append(".*");
                pattern.append(Pattern.quote(parts[i]));
            }
            if (lower.matches(pattern.toString())) return true;
        }
        return false;
    }
}
