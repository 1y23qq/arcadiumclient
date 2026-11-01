// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.colorsaturation;

public final class CategoryColorProfile {
    public float saturation = 1, contrast = 1, brightness = 1, hue;

    public void set(float saturation, float contrast, float brightness, float hue) {
        this.saturation = finite(saturation, 1, -1, 5);
        this.contrast = finite(contrast, 1, 0, 2);
        this.brightness = finite(brightness, 1, 0, 2);
        this.hue = finite(hue, 0, -180, 180);
    }

    public boolean identity() {
        return near(saturation, 1) && near(contrast, 1) && near(brightness, 1) && near(hue, 0);
    }

    public boolean sameAs(CategoryColorProfile other) {
        return near(saturation, other.saturation) && near(contrast, other.contrast)
                && near(brightness, other.brightness) && near(hue, other.hue);
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < 1e-4f; }
    private static float finite(float value, float fallback, float min, float max) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
