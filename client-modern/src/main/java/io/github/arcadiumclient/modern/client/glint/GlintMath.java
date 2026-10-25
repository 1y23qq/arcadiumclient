// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint;

import org.joml.Matrix4f;

public final class GlintMath {
    private GlintMath() { }
    public static float finite(float value, float fallback, float minimum, float maximum) {
        return Float.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback;
    }
    public static Matrix4f texture(GlintEffectOptions options, int stroke, long millis, double nativeSpeed) {
        float speed = finite(options.getSpeed(), 1, 0, 10);
        float scale = finite(options.getScale(), 1, 0, 8) * 8;
        float angle = finite(stroke == 0 ? options.getStrokeRotOne() : options.getStrokeRotTwo(), stroke == 0 ? -50 : 10, -180, 180);
        double period = stroke == 0 ? 3000 : 4873;
        double time = millis * speed * (Double.isFinite(nativeSpeed) ? Math.clamp(nativeSpeed * 2, 0, 2) : 1);
        float offset = (float) ((time % period) / period / 8) * (stroke == 0 ? 1 : -1);
        return new Matrix4f().scale(scale).translate(offset, 0, 0).rotateZ((float) Math.toRadians(angle));
    }
    public static int color(GlintEffectOptions options, int stroke, boolean potionBased, GlintItemData.Data data) {
        if (potionBased && data.potion()) return data.color() | 0xFF000000;
        return options.getIndividualStrokes() ? (stroke == 0 ? options.getStrokeOneColor() : options.getStrokeTwoColor()).getArgb() : options.getGlintColor().getArgb();
    }
}
