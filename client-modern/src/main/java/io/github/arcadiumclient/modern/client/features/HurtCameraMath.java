// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

public final class HurtCameraMath {
    private HurtCameraMath() {}
    public static float angle(float degrees, double multiplier, boolean yawAxis, boolean oldMode) {
        if (oldMode && yawAxis) return 0;
        double value = degrees * (Double.isFinite(multiplier) ? multiplier : 0.3);
        return (float) Math.max(-Float.MAX_VALUE, Math.min(Float.MAX_VALUE, value));
    }
}
