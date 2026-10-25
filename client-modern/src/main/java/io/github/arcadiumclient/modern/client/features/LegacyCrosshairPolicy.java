// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

public final class LegacyCrosshairPolicy {
    private LegacyCrosshairPolicy() {}
    public static boolean hidden(boolean disabled, boolean hideContainers, boolean container, boolean hidePerspective, boolean firstPerson) {
        return disabled || hideContainers && container || hidePerspective && !firstPerson;
    }
    public static int alpha(float opacity) {
        return Math.round(Math.clamp(Float.isFinite(opacity) ? opacity : 100f, 0f, 100f) * 2.55f);
    }
    public static int tint(int alpha, boolean removeBlending) {
        return removeBlending ? alpha << 24 | 0xFFFFFF : 0xFF000000 | alpha << 16 | alpha << 8 | alpha;
    }
}
