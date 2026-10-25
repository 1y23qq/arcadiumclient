// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features;

public final class BackgroundOpacity {
    private BackgroundOpacity() {}

    public static float percent(float value, float fallback) {
        return Math.max(0, Math.min(100, Float.isFinite(value) ? value : fallback));
    }

    public static int withAlpha(int color, float opacity) {
        int alpha = Math.round(percent(opacity, 50) * 255 / 100);
        return (color & 0xFFFFFF) | (alpha << 24);
    }

    public static float containerAlpha(float opacity, boolean bottom, float progress) {
        float percent = percent(opacity, 81.56863f);
        if (!bottom) percent = Math.max(0, percent - 100f * 16 / 255);
        float animation = Float.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 1;
        return percent * animation;
    }
}
