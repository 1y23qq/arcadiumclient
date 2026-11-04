// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.diagnostics;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import jdk.jfr.consumer.*;

public final class CaptureProfile implements AutoCloseable {
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9_.$<>\\[\\]/]");
    static final Set<String> EVENTS = Set.of("jdk.ExecutionSample", "jdk.NativeMethodSample", "jdk.ObjectAllocationSample",
        "jdk.GarbageCollection", "jdk.GCPhasePause", "jdk.JavaMonitorEnter", "jdk.ThreadPark", "jdk.CPUInformation");
    private final long renderThread;
    private RecordingStream stream;
    private volatile Instant start, end;
    private final Map<String, Long> stacks = new HashMap<>(), allocations = new HashMap<>();
    private final Map<String, String> symbols = new HashMap<>();
    private final Map<String, Long> eventCounts = new TreeMap<>();
    private final Map<String, long[]> pauseTotals = new TreeMap<>();
    private final Map<String, Integer> retainedPauses = new HashMap<>();
    private final Map<String, Long> omittedPauseKinds = new TreeMap<>();
    private final List<Map<String, Object>> pauses = new ArrayList<>();
    private long omittedStacks, omittedPauses, missingStacks, allocationBytes, allocationSamples;
    private String status = "Not started", cpuModel = "Not reported";

    public CaptureProfile(long renderThread) { this.renderThread = renderThread; }

    public void open() {
        try {
            RecordingStream next = new RecordingStream(); stream = next;
            next.setMaxSize(32L * 1024 * 1024); next.setMaxAge(Duration.ofSeconds(150));
            next.setOrdered(true); next.setReuse(true);
            next.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10)).withStackTrace();
            next.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(20)).withStackTrace();
            next.enable("jdk.ObjectAllocationSample").with("throttle", "100/s").withStackTrace();
            next.enable("jdk.GarbageCollection").withoutThreshold().withoutStackTrace();
            next.enable("jdk.GCPhasePause").withoutThreshold().withoutStackTrace();
            next.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(20)).withStackTrace();
            next.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(20)).withStackTrace();
            next.enable("jdk.CPUInformation").withPeriod(Duration.ofSeconds(1)).withoutStackTrace();
            next.onEvent(this::accept);
            next.onError(error -> { synchronized (this) { status = "Sampling interrupted (" + error.getClass().getSimpleName() + ")"; } });
            next.startAsync(); status = "JFR sampling active";
        } catch (Throwable unavailable) {
            status = "JFR unavailable (" + unavailable.getClass().getSimpleName() + "); frame timings remain available";
            close();
        }
    }

    public void begin(Instant instant) { start = instant; }
    public void end(Instant instant) { end = instant; }

    private synchronized void accept(RecordedEvent event) {
        String type = event.getEventType().getName();
        if (!EVENTS.contains(type)) return;
        if (type.equals("jdk.CPUInformation")) {
            if (event.hasField("description")) {
                String description = event.getString("description"); int vendor = description.indexOf(", Vendor:");
                cpuModel = cleanHardware((vendor < 0 ? description : description.substring(0, vendor)).replaceFirst("^Brand: *", ""));
            }
            return;
        }
        Instant first = start, last = end, timestamp = event.getStartTime();
        if (first == null || timestamp.isBefore(first) || (last != null && timestamp.isAfter(last))) return;
        eventCounts.merge(type, 1L, Long::sum);
        switch (type) {
            case "jdk.ExecutionSample", "jdk.NativeMethodSample" -> {
                var thread = event.getThread("sampledThread");
                addStack(type.equals("jdk.ExecutionSample") ? "java" : "native", cohort(thread), event.getStackTrace(), 1);
            }
            case "jdk.ObjectAllocationSample" -> {
                long weight = event.hasField("weight") ? Math.max(0, event.getLong("weight")) : 0;
                String name = cachedSymbol(event.getClass("objectClass").getName());
                if (allocations.size() < 1024 || allocations.containsKey(name)) allocations.merge(name, weight, Long::sum);
                allocationBytes += weight; allocationSamples++;
                addStack("allocation", cohort(event.getThread()), event.getStackTrace(), weight);
            }
            case "jdk.GarbageCollection", "jdk.GCPhasePause", "jdk.JavaMonitorEnter", "jdk.ThreadPark" -> {
                if (type.equals("jdk.JavaMonitorEnter") || type.equals("jdk.ThreadPark")) addStack("blocked", cohort(event.getThread()), event.getStackTrace(), event.getDuration().toNanos());
                recordPause(type, Duration.between(first, timestamp).toNanos() / 1e9, event.getDuration().toNanos(), cohort(event.hasField("eventThread") ? event.getThread() : null));
            }
            default -> { }
        }
    }

    synchronized void recordPause(String type, double atSeconds, long nanos, String thread) {
        long[] totals = pauseTotals.computeIfAbsent(type, ignored -> new long[3]);
        totals[0]++; totals[1] += nanos; totals[2] = Math.max(totals[2], nanos);
        int limit = type.equals("jdk.GarbageCollection") || type.equals("jdk.GCPhasePause") ? 2048 : 512;
        int count = retainedPauses.getOrDefault(type, 0);
        if (count >= limit) { omittedPauses++; omittedPauseKinds.merge(type, 1L, Long::sum); return; }
        retainedPauses.put(type, count + 1);
        pauses.add(Map.of("kind", type, "atSeconds", atSeconds, "durationMs", nanos / 1e6, "thread", thread));
    }

    private String cohort(RecordedThread thread) {
        if (thread == null) return "jvm";
        if (thread.getJavaThreadId() == renderThread) return "render";
        String name = thread.getJavaName();
        return name != null && (name.equals("Arcadium performance report") || name.startsWith("JFR Event Stream")) ? "recorder" : "worker";
    }

    private void addStack(String kind, String thread, RecordedStackTrace trace, long amount) {
        if (trace == null || trace.getFrames().isEmpty()) { missingStacks++; return; }
        StringBuilder key = new StringBuilder(kind).append('|').append(thread).append('|');
        var frames = trace.getFrames();
        for (int i = Math.min(48, frames.size()) - 1; i >= 0; i--) {
            var method = frames.get(i).getMethod();
            if (method == null || method.getType() == null) continue;
            if (key.charAt(key.length() - 1) != '|') key.append(';');
            key.append(cachedSymbol(method.getType().getName())).append('.').append(cachedSymbol(method.getName()));
        }
        String value = key.toString();
        if (stacks.size() >= 8192 && !stacks.containsKey(value)) { omittedStacks++; return; }
        stacks.merge(value, amount, Long::sum);
    }

    static String symbol(String value) {
        if (value == null) return "unknown";
        int hidden = value.indexOf("/0x");
        if (hidden >= 0) value = value.substring(0, hidden);
        value = SYMBOL.matcher(value).replaceAll("_");
        return value.substring(0, Math.min(240, value.length()));
    }

    private String cachedSymbol(String value) {
        String cached = symbols.get(value);
        if (cached != null) return cached;
        String clean = symbol(value);
        if (symbols.size() < 32768) symbols.put(value, clean);
        return clean;
    }

    static String cleanHardware(String value) {
        if (value == null) return "Not reported";
        value = value.replaceAll("[\\p{Cntrl}]", " ").strip();
        return value.substring(0, Math.min(256, value.length()));
    }

    public void finish() {
        RecordingStream value = stream;
        if (value != null) {
            try { value.stop(); }
            catch (Throwable failure) { synchronized (this) { status = "Sampling ended early (" + failure.getClass().getSimpleName() + ")"; } }
            finally { close(); }
        }
    }

    @Override public void close() {
        RecordingStream value = stream; stream = null;
        if (value != null) try { value.close(); } catch (Throwable ignored) { }
    }

    public synchronized Map<String, Object> summary() {
        Map<String, Object> durations = new TreeMap<>();
        pauseTotals.forEach((type, total) -> durations.put(type, Map.of("count", total[0], "totalMs", total[1] / 1e6, "maxMs", total[2] / 1e6)));
        return Map.of("status", status, "eventCounts", new TreeMap<>(eventCounts), "cpuModel", cpuModel,
            "omittedUniqueStacks", omittedStacks, "missingStacks", missingStacks, "omittedPauseEvents", omittedPauses,
            "allocationSamples", allocationSamples, "sampledAllocatedBytesEstimate", allocationBytes,
            "pauseDurations", durations, "omittedPauseEventsByKind", new TreeMap<>(omittedPauseKinds));
    }

    public synchronized Map<String, Object> details() {
        List<Map<String, Object>> entries = new ArrayList<>();
        stacks.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).forEach(entry -> {
            String[] parts = entry.getKey().split("\\|", 3);
            entries.add(Map.of("kind", parts[0], "thread", parts[1], "stack", List.of(parts[2].split(";")), "weight", entry.getValue()));
        });
        Map<String, Object> result = new LinkedHashMap<>(summary()); result.put("stacks", entries);
        result.put("allocationsByClass", new TreeMap<>(allocations)); result.put("pauseEvents", new ArrayList<>(pauses));
        result.put("weightUnits", Map.of("java", "samples at 10 ms", "native", "samples at 20 ms", "allocation", "estimated allocated bytes", "blocked", "nanoseconds"));
        result.put("stackOrder", "root to leaf; at most 48 frames; no arguments, source paths, local variables or thread names");
        result.put("pauseDefinition", "GCPhasePause measures stop-the-world pauses; GarbageCollection can also include concurrent work. Do not add the two. Per-event lists are bounded independently by kind; totals and blocked-stack aggregates cover every accepted event.");
        result.put("enabledEvents", new TreeSet<>(EVENTS)); return result;
    }
}
