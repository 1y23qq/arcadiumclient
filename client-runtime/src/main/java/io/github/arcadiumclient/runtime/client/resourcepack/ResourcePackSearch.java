// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.resourcepack;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ResourcePackSearch {
    private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACING = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private ResourcePackSearch() { }

    public static String text(String... fields) {
        return normalize(String.join(" ", fields));
    }

    public static String[] terms(String query) {
        String value = normalize(query);
        return value.isEmpty() ? new String[0] : value.split(" ");
    }

    public static boolean matches(String[] terms, String text) {
        return Arrays.stream(terms).allMatch(text::contains);
    }

    private static String normalize(String value) {
        String clean = FORMATTING.matcher(value).replaceAll("");
        clean = Normalizer.normalize(clean, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return SPACING.matcher(clean).replaceAll(" ").strip();
    }
}
