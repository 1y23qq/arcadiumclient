// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

public final class UnfocusedFpsPolicy {
    private UnfocusedFpsPolicy() {}
    public static int cap(int original, boolean enabled, int configured, boolean active) {
        return !enabled || active ? original : Math.min(original, Math.clamp(configured, 5, 200));
    }
}
