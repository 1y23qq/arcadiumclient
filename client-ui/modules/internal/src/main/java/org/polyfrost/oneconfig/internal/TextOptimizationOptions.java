// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal;

public final class TextOptimizationOptions {
    private static final ThreadLocal<Boolean> IMMEDIATE = ThreadLocal.withInitial(() -> false);
    public static volatile boolean nameMaskCache = true;
    public static volatile boolean fontMetricCache = true;
    public static volatile boolean vanillaFontBatching = true;
    private TextOptimizationOptions() { }

    public static boolean immediateText() { return IMMEDIATE.get(); }
    public static boolean beginImmediateText(Object font) {
        boolean previous = IMMEDIATE.get();
        if (!previous && font instanceof GameFontMetricCounters metrics) metrics.arcadium$flushTextBatches();
        IMMEDIATE.set(true);
        return previous;
    }
    public static void endImmediateText(boolean previous) { IMMEDIATE.set(previous); }
}
