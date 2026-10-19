// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

public final class ArcadiumPrefix {
    public static final char START = '\u2063';
    public static final char END = '\u2064';
    public static final String MARKED = "\u2063Arcadium\u2064";

    private ArcadiumPrefix() { }

    public static String message(String content) { return MARKED + " §7> §f" + content; }
    public static String plainMarkers(String text) {
        return text == null ? null : text.replace("\u2063", "").replace("\u2064", "");
    }

    public static int color(int character, long nanos, boolean animated, int start, int end) {
        if (!animated) return 0xFF000000 | start;
        double phase = (nanos % 4000000000L) / 4000000000.0;
        double t = (Math.sin((phase + character / 16.0) * Math.PI * 2) + 1) * 0.5;
        int r = mix(start >> 16 & 255, end >> 16 & 255, t);
        int g = mix(start >> 8 & 255, end >> 8 & 255, t);
        int b = mix(start & 255, end & 255, t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, double t) { return (int) Math.round(a + (b - a) * t); }
}
