// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChatFilterPolicy {
    private static int configuredHistoryLimit = 1000;
    private final List<String> words;
    private final boolean caseSensitive;
    private final boolean wholeWords;

    public ChatFilterPolicy(List<String> input, boolean caseSensitive, boolean wholeWords) {
        this.caseSensitive = caseSensitive;
        this.wholeWords = wholeWords;
        ArrayList<String> clean = new ArrayList<>();
        if (input != null) for (String value : input) {
            if (value == null || value.isBlank()) continue;
            String word = plain(value).trim();
            if (word.isEmpty() || word.length() > 128) continue;
            if (!caseSensitive) word = word.toLowerCase(Locale.ROOT);
            if (!clean.contains(word)) clean.add(word);
            if (clean.size() >= 256) break;
        }
        this.words = List.copyOf(clean);
    }

    public boolean hides(String message) {
        if (message == null || words.isEmpty()) return false;
        String text = plain(message);
        if (!caseSensitive) text = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            int at = text.indexOf(word);
            while (at >= 0) {
                int end = at + word.length();
                if (!wholeWords || ((at == 0 || !wordPart(text.codePointBefore(at)))
                        && (end == text.length() || !wordPart(text.codePointAt(end))))) return true;
                at = text.indexOf(word, at + 1);
            }
        }
        return false;
    }

    public static int historyLimit(int existingSize) {
        return historyLimit(existingSize, configuredHistoryLimit);
    }

    public static void setHistoryLimit(int configured) {
        configuredHistoryLimit = Math.max(100, Math.min(16384, configured));
    }

    public static int historyLimit(int existingSize, int configured) {
        return configured > 0 ? configured : Math.max(100, existingSize);
    }

    private static boolean wordPart(int character) {
        int type = Character.getType(character);
        return Character.isLetterOrDigit(character) || character == '_'
                || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    public static String plain(String text) {
        if (text == null) return "";
        if (text.indexOf('\u00a7') < 0 && text.indexOf('\u2063') < 0 && text.indexOf('\u2064') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) { i++; continue; }
            if (c != '\u2063' && c != '\u2064') out.append(c);
        }
        return out.toString();
    }
}
