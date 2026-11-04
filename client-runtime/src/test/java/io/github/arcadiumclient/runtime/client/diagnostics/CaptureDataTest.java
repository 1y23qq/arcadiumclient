// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.diagnostics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureDataTest {
    @Test void foregroundStatsExcludeMenusAndLostFocusWithoutLosingRawFrames() {
        var data = new CaptureData(Map.of(), Map.of(), Map.of(), 8);
        long[] times = new long[10], hits = new long[10]; times[3] = 500_000; hits[3] = 1;
        data.frame(10, 1_000_000, 3, times, hits, 400_000, 1_000_000);
        data.frame(20, 90_000_000, 7, times, hits, 500_000, 90_000_000);
        data.frame(30, 90_000_000, 1, times, hits, 500_000, 90_000_000);
        assertEquals(3, data.rows); assertEquals(1, data.foreground.count); assertEquals(2, data.other.count);
        assertEquals(1000.0, data.foreground.summary().get("fps")); assertEquals(500_000, data.phaseTotal[3]);
        assertEquals(.4, data.measuredCpuNs / (double) data.measuredWallNs);
    }

    @Test void rawCapacityDoesNotTruncateAggregateStatisticsAndClosedCaptureIsImmutable() {
        var data = new CaptureData(Map.of(), Map.of(), Map.of(), 2);
        for (int i = 0; i < 1000; i++) data.frame(i, 1_000_000, 3, new long[10], new long[10], -1, 1_000_000);
        assertEquals(2, data.rows); assertEquals(998, data.droppedRows); assertEquals(1000, data.foreground.count);
        data.close(1_000_000_000L, "manual");
        data.frame(2000, 99_000_000, 3, new long[10], new long[10], -1, 1);
        data.point(Map.of("atSeconds", 2)); data.gpu(1, 1); data.change(1, Map.of("changed", true));
        assertEquals(1000, data.all.count); assertTrue(data.timeline.isEmpty()); assertTrue(data.gpu.isEmpty()); assertTrue(data.settingsChanges.isEmpty());
    }

    @Test void lowFpsUsesSlowestFrameMeanInsteadOfFpsPercentile() {
        var stats = new CaptureData.Stats();
        for (int i = 0; i < 990; i++) stats.add(1_000_000);
        for (int i = 0; i < 10; i++) stats.add(10_000_000);
        assertEquals(100, stats.low(), 1e-9); assertEquals(1, stats.percentile(.99), 1e-9);
        assertEquals(1000_000_000.0 / 1_090_000, (double) stats.summary().get("fps"), .00001);
    }

    @Test void missingHooksAreNotAdvertisedAsZeroCostMeasurements() {
        var data = new CaptureData(Map.of(), Map.of(), Map.of(), 1);
        var report = data.report(60, Map.of());
        @SuppressWarnings("unchecked") var timings = (List<Map<String, Object>>) report.get("mainThreadTimings");
        assertTrue(timings.stream().allMatch(value -> Boolean.FALSE.equals(value.get("observed")) && !value.containsKey("totalMs")));
        assertEquals(0.0, data.foreground.summary().get("fps"));
    }
}
