// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.client.features;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

public final class PasswordMask {
    private PasswordMask() { }
    private record Commands(String source, Set<String> names) { }
    private static volatile Commands cached = new Commands("", Set.of());

    private static Set<String> names(String source) {
        Commands current = cached;
        if (!current.source.equals(source)) {
            Set<String> names = new HashSet<>();
            for (String name : source.split("[,\\s]+")) {
                if (name.startsWith("/")) name = name.substring(1);
                if (!name.isEmpty()) names.add(name.toLowerCase(Locale.ROOT));
            }
            current = new Commands(source, Set.copyOf(names));
            cached = current;
        }
        return current.names;
    }

    public static String display(String text, String commands) {
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        if (start == text.length() || text.charAt(start) != '/') return text;
        int end = start + 1;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        if (end == text.length()) return text;
        String command = text.substring(start + 1, end).toLowerCase(Locale.ROOT);
        command = command.substring(command.lastIndexOf(':') + 1);
        if (!names(commands).contains(command)) return text;
        char[] masked = text.toCharArray();
        for (int i = end; i < masked.length; i++) if (!Character.isWhitespace(masked[i])) masked[i] = '*';
        return new String(masked);
    }
}
