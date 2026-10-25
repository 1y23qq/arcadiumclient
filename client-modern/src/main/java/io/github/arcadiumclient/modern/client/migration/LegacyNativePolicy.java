// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import com.google.gson.JsonObject;
import java.util.Locale;

public final class LegacyNativePolicy {
    private LegacyNativePolicy() { }
    public static int blurRadius(boolean enabled, int stored) { return enabled ? Math.clamp(stored, 0, 20) : 0; }
    public static double scrollingStrength(boolean pushBack, double stored) { return pushBack ? Math.clamp(Double.isFinite(stored) ? stored : 1, .1, 2) : 0; }
    public static boolean coreBlurSchema(JsonObject root) {
        return root.has("gradientStartColor") || root.has("gradientEndColor")
            || root.has("blurAnimationCurve") && root.get("blurAnimationCurve").isJsonObject()
            || root.has("backgroundAnimationCurve") && root.get("backgroundAnimationCurve").isJsonObject();
    }
    public static String rgb(int argb) { return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF); }
    public static int argb(String rgb, int alpha) { return Math.clamp(alpha, 0, 255) << 24 | Integer.parseUnsignedInt(rgb.replace("#", ""), 16) & 0xFFFFFF; }
}
