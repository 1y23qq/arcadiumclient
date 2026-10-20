// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance;

public final class LegacyPerformance {
    private static boolean fontBatchEnabled = !Boolean.getBoolean("arcadium.legacyPerformance.disableFontBatch");
    private static final boolean KEEP_FRAME_YIELD = Boolean.getBoolean("arcadium.legacyPerformance.keepFrameYield");

    private LegacyPerformance() {}

    public static boolean fontBatchEnabled() {
        return fontBatchEnabled;
    }

    public static void endFrame() {
        if (KEEP_FRAME_YIELD) Thread.yield();
    }
}
