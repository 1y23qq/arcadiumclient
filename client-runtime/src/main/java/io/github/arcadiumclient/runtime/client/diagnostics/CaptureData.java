// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.diagnostics;

import java.util.*;

public final class CaptureData {
    public static final String[] PHASES = {"tick", "render", "world", "entities", "terrain", "particles", "hud", "present", "fps_wait", "chunk_updates"};
    public static final int MAX_FRAMES = 262144;
    public static final int WORLD = 1, FOCUSED = 2, SCREEN = 4;
    final long[] offsets, intervals;
    final int[][] phases;
    final int[] flags, threadCpu;
    final List<Map<String, Object>> timeline = new ArrayList<>(), gpu = new ArrayList<>();
    final List<Map<String, Object>> settingsChanges = new ArrayList<>();
    final Map<String, Object> environment;
    Map<String, Object> settings;
    Map<String, Long> countersBefore;
    volatile Map<String, Long> countersAfter = Map.of();
    Map<String, Object> optimizationStateAtStart = Map.of();
    volatile Map<String, Object> optimizationStateLatest = Map.of();
    final Stats all = new Stats(), foreground = new Stats(), other = new Stats();
    final long[] phaseTotal = new long[PHASES.length], phaseHits = new long[PHASES.length];
    final long[] allPhaseTotal = new long[PHASES.length], allPhaseHits = new long[PHASES.length];
    final int capacity;
    int rows, worldChanges, gpuDropped, processSamplesFailed;
    long droppedRows, incompleteFrames, measuredCpuNs, measuredWallNs, measuredCpuFrames;
    long durationNs;
    long contextCollectionNs;
    boolean closed;
    String stopReason = "duration";
    String gpuState = "Not initialized";

    public CaptureData(Map<String, Object> environment, Map<String, Object> settings, Map<String, Long> counters) {
        this(environment, settings, counters, MAX_FRAMES);
    }

    CaptureData(Map<String, Object> environment, Map<String, Object> settings, Map<String, Long> counters, int capacity) {
        this.environment = Map.copyOf(environment); this.settings = Map.copyOf(settings); countersBefore = Map.copyOf(counters);
        this.capacity = Math.max(1, Math.min(MAX_FRAMES, capacity));
        offsets = new long[this.capacity]; intervals = new long[this.capacity]; flags = new int[this.capacity];
        threadCpu = new int[this.capacity]; phases = new int[PHASES.length][this.capacity];
    }

    public synchronized void frame(long offset, long interval, int state, long[] times, long[] hits, long cpuNs, long wallNs) {
        if (closed || interval <= 0 || offset < 0) return;
        all.add(interval);
        for (int i = 0; i < PHASES.length; i++) { allPhaseTotal[i] += Math.max(0, times[i]); allPhaseHits[i] += hits[i]; }
        boolean included = (state & (WORLD | FOCUSED | SCREEN)) == (WORLD | FOCUSED);
        (included ? foreground : other).add(interval);
        if (included) for (int i = 0; i < PHASES.length; i++) { phaseTotal[i] += Math.max(0, times[i]); phaseHits[i] += hits[i]; }
        if (cpuNs >= 0 && wallNs > 0 && included) { measuredCpuNs += cpuNs; measuredWallNs += wallNs; measuredCpuFrames++; }
        if (rows >= capacity) { droppedRows++; return; }
        offsets[rows] = offset; intervals[rows] = interval; flags[rows] = state;
        threadCpu[rows] = cpuNs < 0 ? -1 : micros(cpuNs);
        for (int i = 0; i < PHASES.length; i++) phases[i][rows] = micros(times[i]);
        rows++;
    }

    private static int micros(long nanos) { return (int) Math.max(0, Math.min(Integer.MAX_VALUE, nanos / 1000)); }

    public synchronized void point(Map<String, Object> value) { if (!closed && timeline.size() < 125) timeline.add(Map.copyOf(value)); }
    public synchronized void change(long offset, Map<String, Object> value) {
        if (!closed && settingsChanges.size() < 32) settingsChanges.add(Map.of("atSeconds", offset / 1e9, "settings", value));
    }
    public synchronized void gpu(long offset, long nanos) {
        if (!closed && nanos >= 0 && gpu.size() < 20000) gpu.add(Map.of("atSeconds", offset / 1e9, "queuedRenderMs", nanos / 1e6));
    }
    public synchronized void close(long duration, String reason) { closed = true; durationNs = Math.max(0, duration); stopReason = reason; }

    public Map<String, Object> report(int requestedSeconds, Map<String, Object> profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", "arcadium.performance.v1");
        result.put("capture", Map.of("requestedSeconds", requestedSeconds, "recordedSeconds", durationNs / 1e9,
            "stopReason", stopReason, "warmupSeconds", 5, "worldChanges", worldChanges,
            "rawFrames", rows, "rawFramesOmitted", droppedRows, "incompleteOrTransitionFrames", incompleteFrames));
        result.put("environment", environment); result.put("settingsAtStart", settings); result.put("settingsChanges", settingsChanges);
        result.put("optimizationStateAtStart", optimizationStateAtStart); result.put("optimizationStateLatest", optimizationStateLatest);
        result.put("contextCollectionMs", contextCollectionNs / 1e6);
        result.put("processSamplesFailed", processSamplesFailed);
        result.put("frames", Map.of("all", all.summary(), "foregroundWorldWithoutScreen", foreground.summary(), "other", other.summary()));
        result.put("mainThreadTimings", timings(phaseTotal, phaseHits, foreground.count));
        result.put("allMainThreadTimings", timings(allPhaseTotal, allPhaseHits, all.count));
        result.put("mainThreadTimingsScope", "mainThreadTimings includes foreground world frames without a screen; allMainThreadTimings also includes menus and unfocused frames.");
        result.put("timingDefinition", "Inclusive elapsed time on the main thread. Nested phases overlap and include driver waits; do not add them together.");
        result.put("renderThreadCpu", Map.of("sampledFrames", measuredCpuFrames, "cpuMs", measuredCpuNs / 1e6,
            "wallMs", measuredWallNs / 1e6, "cpuFraction", measuredWallNs == 0 ? 0 : measuredCpuNs / (double) measuredWallNs,
            "definition", "Platform CPU accounting sampled every 16 frames; short intervals can be quantized. Use timeline CPU deltas for capture-wide utilization.",
            "timelineCpuFraction", timelineCpuFraction()));
        result.put("gpu", Map.of("status", gpuState, "sampleEveryFrames", 16, "samples", gpu,
            "skippedBusySlots", gpuDropped, "definition", "Asynchronous timestamp interval over queued rendering, including submission gaps. Not GPU utilization. No glFinish or blocking query read is used."));
        Map<String, Object> counters = new TreeMap<>();
        countersAfter.forEach((name, value) -> {
            long before = countersBefore.getOrDefault(name, value);
            counters.put(name, Map.of("before", before, "after", value, "difference", value - before));
        });
        result.put("optimizationCounters", counters);
        result.put("counterDefinition", "Raw counter differences from the last context sample in this capture. Counts measure operations, not FPS saved. Visible-count gauges can decrease; negative differences can also indicate a reset.");
        result.put("timeline", timeline); result.put("profileSummary", profile); result.put("observations", observations());
        result.put("privacy", Map.of("automaticUpload", false, "rawJfrIncluded", false, "rawLogsIncluded", false,
            "excluded", List.of("chat", "credentials", "account names and UUIDs", "server addresses", "world names and seeds", "coordinates", "file paths", "screenshots", "JVM command line", "environment variables", "thread names", "text and keybind settings")));
        result.put("limitations", List.of("Profiling adds overhead. This capture is for finding expensive code, not a controlled FPS comparison.",
            "No hardware temperature, power or device-utilization claim is made. Driver time and CPU/GPU overlap can require a follow-up test.",
            "Only foreground world frames without an open screen enter the primary FPS summary. Profiles and the timeline cover the full recording.",
            "All visual and game settings are left unchanged. Different scenes and FPS caps can dominate comparisons."));
        return result;
    }

    private static List<Map<String, Object>> timings(long[] totals, long[] hits, long frames) {
        List<Map<String, Object>> timings = new ArrayList<>();
        for (int i = 0; i < PHASES.length; i++) {
            Map<String, Object> timing = new LinkedHashMap<>(); timing.put("phase", PHASES[i]); timing.put("calls", hits[i]);
            timing.put("observed", hits[i] > 0);
            if (hits[i] > 0) { timing.put("totalMs", totals[i] / 1e6); timing.put("msPerFrame", totals[i] / (1e6 * Math.max(1, frames))); }
            timings.add(timing);
        }
        return timings;
    }

    private double timelineCpuFraction() {
        Map<String, Object> first = null, last = null;
        for (var point : timeline) if (point.get("renderThreadCpuNs") instanceof Number cpu && cpu.longValue() >= 0) {
            if (first == null) first = point;
            last = point;
        }
        if (first == null || last == null) return -1;
        double seconds = ((Number) last.get("atSeconds")).doubleValue() - ((Number) first.get("atSeconds")).doubleValue();
        long cpu = ((Number) last.get("renderThreadCpuNs")).longValue() - ((Number) first.get("renderThreadCpuNs")).longValue();
        return seconds < 5 || cpu < 0 ? -1 : cpu / (1e9 * seconds);
    }

    List<String> observations() {
        List<String> out = new ArrayList<>();
        if (foreground.count < 300 || foreground.sum < 5_000_000_000L) out.add("Too little foreground gameplay was recorded for a useful performance comparison.");
        if (other.count > all.count / 10) out.add("Menus or lost focus affected more than 10% of frames; inspect the flags and timeline.");
        if (worldChanges > 0) out.add("The world changed during capture; loading and gameplay should be analyzed separately.");
        if (!settingsChanges.isEmpty()) out.add("Settings changed during capture; this is not a fixed-settings baseline.");
        if (droppedRows > 0) out.add("The raw frame table reached its limit. Aggregate histograms still cover every accepted frame.");
        long max = 0; int largest = -1;
        for (int i : new int[]{0, 3, 4, 5, 6, 7, 8, 9}) if (phaseTotal[i] > max) { max = phaseTotal[i]; largest = i; }
        if (largest >= 0) out.add("Largest measured non-parent main-thread phase: " + PHASES[largest] + ". Use sampled stacks to identify the code inside it.");
        double cpuFraction = timelineCpuFraction();
        if (cpuFraction >= 0 && cpuFraction < .5) out.add("Over the timeline sample interval, the main thread spent more than half its wall time off CPU. Check frame caps, presentation, GPU/driver waits and OS scheduling before optimizing Java calculations.");
        return out;
    }

    static final class Stats {
        final long[] histogram = new long[10064], histogramNs = new long[10064];
        long count, sum, maximum, over16, over33, over50;
        void add(long ns) {
            if (ns <= 0) return;
            count++; sum += ns; maximum = Math.max(maximum, ns);
            if (ns > 16_666_667) over16++; if (ns > 33_333_333) over33++; if (ns > 50_000_000) over50++;
            int bin = ns < 100_000_000L ? (int) (ns / 10000) : 10000 + Math.min(63, 63 - Long.numberOfLeadingZeros(ns / 100_000_000L));
            histogram[bin]++; histogramNs[bin] += ns;
        }
        double percentile(double fraction) {
            long target = (long) Math.ceil(count * fraction), total = 0;
            for (int i = 0; i < histogram.length; i++) if ((total += histogram[i]) >= target && histogram[i] > 0) return histogramNs[i] / (1e6 * histogram[i]);
            return 0;
        }
        double low() {
            long needed = Math.max(1, (count + 99) / 100), remaining = needed; double ns = 0;
            for (int i = histogram.length - 1; i >= 0 && remaining > 0; i--) {
                long take = Math.min(remaining, histogram[i]);
                if (take > 0) { ns += take * (histogramNs[i] / (double) histogram[i]); remaining -= take; }
            }
            return ns == 0 ? 0 : 1e9 * needed / ns;
        }
        Map<String, Object> summary() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("count", count); out.put("includedSeconds", sum / 1e9); out.put("fps", sum == 0 ? 0 : 1e9 * count / sum);
            out.put("meanFrameMs", count == 0 ? 0 : sum / (1e6 * count)); out.put("onePercentLowFps", low());
            out.put("p50FrameMs", percentile(.5)); out.put("p95FrameMs", percentile(.95)); out.put("p99FrameMs", percentile(.99));
            out.put("maxFrameMs", maximum / 1e6); out.put("over16_67Ms", over16); out.put("over33_33Ms", over33); out.put("over50Ms", over50);
            out.put("quantiles", "Histogram estimates: 0.01 ms bins below 100 ms, powers of two above. 1% low is the reciprocal of the estimated slowest ceil(1%) mean.");
            return out;
        }
    }
}
