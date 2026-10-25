// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

public final class VignettePolicy {
    private VignettePolicy() {}
    private static float finite(float value, float fallback) { return Float.isFinite(value) ? value : fallback; }
    public static float strength(int type, float original, float fixed, float minimum, float maximum, float multiplier) {
        if (type != 1) return Math.clamp(finite(fixed, 50) / 100f, 0, 1);
        float low = Math.clamp(finite(minimum, 0) / 100f, 0, 1), high = Math.clamp(finite(maximum, 100) / 100f, 0, 1);
        return Math.clamp(finite(original, 0) * finite(multiplier, 1), Math.min(low, high), Math.max(low, high));
    }
    public static float condition(float percentage, float threshold, float minimum, float maximum) {
        if (!Float.isFinite(percentage) || percentage > threshold) return 0;
        float progress = threshold <= 0 ? 1 : Math.clamp(1 - percentage / threshold, 0, 1);
        return Math.clamp((minimum + progress * (maximum - minimum)) / 100f, 0, 1);
    }
    public static int tint(int original, int color, float strength) {
        if (strength <= 0) return original;
        int r = Math.round((255 - (color >>> 16 & 255)) * strength);
        int g = Math.round((255 - (color >>> 8 & 255)) * strength);
        int b = Math.round((255 - (color & 255)) * strength);
        return original & 0xFF000000 | Math.clamp(r, 0, 255) << 16 | Math.clamp(g, 0, 255) << 8 | Math.clamp(b, 0, 255);
    }
}
