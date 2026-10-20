// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.runtime.legacy;

import java.util.Arrays;

public final class LegacyVisualMath {
    private LegacyVisualMath() {}

    private static float influence(float percent) { return Float.isFinite(percent) ? percent / 100f : 1f; }

    public static float movementFov(double attributeSpeed, float walkSpeed, boolean sprinting, boolean flying,
            int bowTicks, float speedPercent, float sprintPercent, float flyingPercent, float aimingPercent) {
        float result = flying ? 1f + .1f * influence(flyingPercent) : 1f;
        float speed = (float) attributeSpeed;
        if (speed != walkSpeed) {
            float adjusted;
            if (sprinting) {
                float effect = (speed / 1.3f - walkSpeed) * influence(speedPercent);
                adjusted = (walkSpeed + effect) * (1f + .3f * influence(sprintPercent));
            } else adjusted = walkSpeed + (speed - walkSpeed) * influence(speedPercent);
            result *= (float) (((double) adjusted / walkSpeed + 1.0) / 2.0);
        }
        if (walkSpeed == 0 || !Float.isFinite(result)) result = 1f;
        if (bowTicks >= 0) {
            float draw = Math.min(1f, bowTicks / 20f);
            result *= 1f - draw * draw * .15f * influence(aimingPercent);
        }
        return result;
    }

    public static float submergedFov(float vanilla, float percent) { return 70f - (70f - vanilla) * influence(percent); }

    public static void applyLightLevel(int[] lightMap, boolean enabled, int level) {
        if (!enabled || lightMap.length < 256) return;
        int clamped = Math.max(0, Math.min(15, level));
        Arrays.fill(lightMap, lightMap[clamped * 17]);
    }
}
