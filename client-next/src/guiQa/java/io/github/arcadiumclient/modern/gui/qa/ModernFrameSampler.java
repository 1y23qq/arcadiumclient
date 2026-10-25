// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class ModernFrameSampler {
    private static final long[] SAMPLES = new long[250_000];
    private static boolean sampling;
    private static int count;
    private static int inactive;
    private static int overflow;
    private static long previous;
    private static long frames;

    private ModernFrameSampler() {}

    static void start() {
        count = inactive = overflow = 0;
        previous = 0;
        sampling = true;
    }

    public static void renderedFrame() {
        frames++;
        if (!sampling) return;
        var minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        if (!minecraft.isWindowActive() || minecraft.level == null || minecraft.gui.screen() != null || minecraft.gui.overlay() != null) {
            inactive++;
            previous = 0;
            return;
        }
        if (previous != 0) {
            if (count < SAMPLES.length) SAMPLES[count++] = now - previous;
            else overflow++;
        }
        previous = now;
    }

    static long renderedFrames() { return frames; }

    static Map<String, Object> finish() {
        sampling = false;
        long[] sorted = Arrays.copyOf(SAMPLES, count);
        Arrays.sort(sorted);
        var report = new LinkedHashMap<String, Object>();
        report.put("samples", count);
        report.put("inactiveOrOverlayFrames", inactive);
        report.put("overflowFrames", overflow);
        report.put("frameBoundary", "GameRenderer.render return to return");
        if (count > 0) {
            report.put("medianMs", percentile(sorted, .5));
            report.put("p95Ms", percentile(sorted, .95));
            report.put("p99Ms", percentile(sorted, .99));
            report.put("minMs", sorted[0] / 1_000_000d);
            report.put("maxMs", sorted[count - 1] / 1_000_000d);
            report.put("firstFrameIntervalsNs", Arrays.copyOf(SAMPLES, Math.min(count, 64)));
        }
        return report;
    }

    private static double percentile(long[] sorted, double percentile) {
        return sorted[(int) Math.ceil((sorted.length - 1) * percentile)] / 1_000_000d;
    }
}
