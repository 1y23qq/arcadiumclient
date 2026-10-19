// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

public final class HeightLimitText {
    private HeightLimitText() { }

    public static String format(int style, int height, Integer limit, boolean showLimit,
                                boolean showCurrent, boolean showDistance) {
        if (style != 1) return height + " / " + (limit == null ? "?" : limit);
        StringBuilder text = new StringBuilder();
        if (showCurrent) text.append(height);
        if (showLimit) {
            if (!text.isEmpty()) text.append(" / ");
            text.append(limit == null ? "?" : limit);
        }
        if (showDistance) {
            if (!text.isEmpty()) text.append(' ');
            text.append('(').append(limit == null ? "?" : (long) limit - height).append(')');
        }
        return text.toString();
    }
}
