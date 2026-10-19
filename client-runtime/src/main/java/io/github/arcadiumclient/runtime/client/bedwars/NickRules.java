// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.util.List;
import java.util.Locale;

public final class NickRules {
    private NickRules() { }

    public static boolean matches(String name, String current, boolean four, boolean vowels, boolean edges,
                                  boolean custom, List<String> exact, List<String> starts, List<String> ends, List<String> contains) {
        if (!PlayerNameUtil.valid(name) || name.equalsIgnoreCase(current)) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (four && name.length() == 4) return true;
        if (vowels) for (char vowel : new char[]{'a', 'e', 'i', 'o', 'u'}) {
            if (lower.contains("" + vowel + vowel + vowel)) return true;
        }
        if (edges && (name.startsWith("_") || name.endsWith("_"))) return true;
        if (!custom) return false;
        if (exact.contains(lower)) return true;
        for (String value : starts) if (lower.startsWith(value)) return true;
        for (String value : ends) if (lower.endsWith(value)) return true;
        for (String value : contains) if (lower.contains(value)) return true;
        return false;
    }
}
