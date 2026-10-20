// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

public final class LegacyFormatting {
    private LegacyFormatting() {}
    public static String last(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '§') continue;
            char code = Character.toLowerCase(text.charAt(++i));
            if ("0123456789abcdefr".indexOf(code) >= 0) result.setLength(0);
            if ("0123456789abcdefklmnor".indexOf(code) >= 0) result.append('§').append(code);
        }
        return result.toString();
    }
}
