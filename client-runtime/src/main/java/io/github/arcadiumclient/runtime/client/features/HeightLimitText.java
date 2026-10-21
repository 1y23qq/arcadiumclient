// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeightLimitText {
    public enum Kind { TEXT, LIMIT, PARENTHESES, DISTANCE }
    public record Part(String text, Kind kind) { }
    private static final Pattern TOKENS = Pattern.compile("#current|#limit|#distance|[()]");
    private HeightLimitText() { }

    public static long distance(int height, int limit) { return Math.max(0L, (long) limit - height); }

    public static int distanceRange(long distance, int warning, int danger) {
        int near = Math.max(0, danger);
        return distance <= near ? 2 : distance <= Math.max(near, warning) ? 1 : 0;
    }

    public static List<Part> parts(String template, int height, Integer limit) {
        if (template == null) template = "";
        if (template.length() > 512) template = template.substring(0, 512);
        List<Part> parts = new ArrayList<>();
        Matcher matcher = TOKENS.matcher(template);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) parts.add(new Part(template.substring(cursor, matcher.start()), Kind.TEXT));
            String token = matcher.group();
            parts.add(switch (token) {
                case "#current" -> new Part(Integer.toString(height), Kind.TEXT);
                case "#limit" -> new Part(limit == null ? "?" : limit.toString(), Kind.LIMIT);
                case "#distance" -> new Part(limit == null ? "?" : Long.toString(distance(height, limit)), Kind.DISTANCE);
                default -> new Part(token, Kind.PARENTHESES);
            });
            cursor = matcher.end();
        }
        if (cursor < template.length()) parts.add(new Part(template.substring(cursor), Kind.TEXT));
        return List.copyOf(parts);
    }

    public static String template(int style, boolean showLimit, boolean showCurrent, boolean showDistance) {
        if (style != 1) return "#current / #limit";
        StringBuilder text = new StringBuilder();
        if (showCurrent) text.append("#current");
        if (showLimit) {
            if (!text.isEmpty()) text.append(" / ");
            text.append("#limit");
        }
        if (showDistance) text.append("(#distance)");
        return text.toString();
    }

    public static String format(int style, int height, Integer limit, boolean showLimit,
                                boolean showCurrent, boolean showDistance) {
        return parts(template(style, showLimit, showCurrent, showDistance), height, limit).stream()
                .map(Part::text).collect(java.util.stream.Collectors.joining());
    }
}
