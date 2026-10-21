// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.runtime.client.features;

public final class UnfocusedFrameLimit {
    private UnfocusedFrameLimit() { }
    public static int apply(int original, boolean focused, int configured) {
        if (focused) return original;
        int cap = Math.max(5, Math.min(200, configured));
        return original <= 0 ? cap : Math.min(original, cap);
    }
}
