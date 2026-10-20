package org.codeberg.chromatic.freelook.util;

import net.minecraft.util.Mth;

public class EasingProgressTimer {

    private final long durationMs;
    private long startTime = -1L;
    private boolean active = false;

    private static final double EASING_STRENGTH = 10.0;

    public EasingProgressTimer(long durationMs) {
        this.durationMs = durationMs;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.active = true;
    }

    public void stop() {
        this.active = false;
    }

    public boolean isComplete() {
        return active && getElapsed() >= durationMs;
    }

    public float getProgress() {
        if (!active || startTime < 0) {
            return 0f;
        }

        long elapsed = getElapsed();
        if (elapsed >= durationMs) {
            return 1f;
        }

        float linear = Mth.clamp((float) elapsed / durationMs, 0f, 1f);

        float eased = (float) (1.0 - Math.pow(2.0, -EASING_STRENGTH * linear));

        return Mth.clamp(eased, 0f, 1f);
    }

    private long getElapsed() {
        return System.currentTimeMillis() - startTime;
    }
}
