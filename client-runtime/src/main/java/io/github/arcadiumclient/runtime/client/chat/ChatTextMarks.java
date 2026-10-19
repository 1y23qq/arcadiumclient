// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.chat;

public final class ChatTextMarks {
    public static final char COUNTER = '\u2066', TIMESTAMP = '\u2068', END = '\u2069';
    private ChatTextMarks() { }

    public static boolean marker(char value) { return value == COUNTER || value == TIMESTAMP || value == END; }

    public static String strip(String value) {
        if (value == null || value.indexOf(COUNTER) < 0 && value.indexOf(TIMESTAMP) < 0 && value.indexOf(END) < 0) return value;
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) if (!marker(value.charAt(i))) result.append(value.charAt(i));
        return result.toString();
    }

    public static String counter(long count, int format) {
        String number = Long.toString(count);
        if (format == 8) return "x" + number;
        if (format == 9) return number + "x";
        int choice = Math.max(0, Math.min(7, format));
        char left = "([{<".charAt(choice % 4), right = ")]}>".charAt(choice % 4);
        return "" + left + (choice >= 4 ? "x" : "") + number + right;
    }

    public static String withoutColors(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\u00a7' && i + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(i + 1));
                if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f') { i++; continue; }
            }
            result.append(character);
        }
        return result.toString();
    }
}
