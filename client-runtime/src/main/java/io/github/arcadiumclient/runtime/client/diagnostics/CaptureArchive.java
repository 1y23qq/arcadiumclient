// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

public final class CaptureArchive {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private CaptureArchive() { }

    public static Path write(Path directory, CaptureData data, CaptureProfile profile, int requestedSeconds) throws IOException {
        Files.createDirectories(directory);
        String name = "Arcadium-performance-1.8.9-" + NAME.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
        Path target = directory.resolve(name), temporary = directory.resolve(name + ".partial");
        Map<String, Object> report = data.report(requestedSeconds, profile.summary());
        Map<String, Object> manifest = new LinkedHashMap<>();
        try {
            try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW), StandardCharsets.UTF_8)) {
                add(archive, manifest, "report.json", writer -> JSON.toJson(report, writer));
                add(archive, manifest, "profile.json", writer -> JSON.toJson(profile.details(), writer));
                add(archive, manifest, "frames.csv", writer -> {
                    writer.write("at_ms,frame_ms,flags,render_thread_cpu_ms");
                    for (String phase : CaptureData.PHASES) writer.write("," + phase + "_ms");
                    writer.write('\n');
                    for (int row = 0; row < data.rows; row++) {
                        writer.write(data.offsets[row] / 1e6 + "," + data.intervals[row] / 1e6 + "," + data.flags[row] + "," + (data.threadCpu[row] < 0 ? "" : data.threadCpu[row] / 1000.0));
                        for (int[] phase : data.phases) writer.write("," + phase[row] / 1000.0);
                        writer.write('\n');
                    }
                });
                add(archive, manifest, "timeline.csv", writer -> {
                    if (data.timeline.isEmpty()) return;
                    List<String> columns = new ArrayList<>(data.timeline.getFirst().keySet()); Collections.sort(columns);
                    writer.write(String.join(",", columns)); writer.write('\n');
                    for (var point : data.timeline) {
                        for (int i = 0; i < columns.size(); i++) {
                            if (i > 0) writer.write(',');
                            Object value = point.get(columns.get(i));
                            if (value instanceof Number || value instanceof Boolean) writer.write(value.toString());
                        }
                        writer.write('\n');
                    }
                });
                add(archive, manifest, "summary.txt", writer -> {
                    writer.write("Arcadium performance report / Minecraft 1.8.9\n\n");
                    writer.write("Recorded: " + data.durationNs / 1e9 + " seconds\n");
                    writer.write("GPU: " + data.environment.getOrDefault("glRenderer", "Unknown") + "\n");
                    writer.write("CPU: " + profile.summary().get("cpuModel") + "\n");
                    var frames = data.foreground.summary();
                    writer.write("Foreground gameplay: " + frames.get("count") + " frames\n");
                    writer.write("Average FPS: " + frames.get("fps") + "\nEstimated 1% low FPS: " + frames.get("onePercentLowFps") + "\n");
                    writer.write("p99 frame time: " + frames.get("p99FrameMs") + " ms\n\n");
                    for (String observation : data.observations()) writer.write("* " + observation + "\n");
                    writer.write("\nThis identifies expensive work. It is not a controlled before/after benchmark, and profiling has overhead.\n");
                });
                add(archive, manifest, "README.txt", writer -> writer.write("""
                    Send this ZIP to the Arcadium developer. Nothing is uploaded automatically.

                    report.json: hardware, numeric/boolean settings, frame statistics, inclusive main-thread phases,
                    optimization counter differences and scene/memory/CPU timeline.
                    profile.json: sampled Java/native code stacks, estimated allocations and GC/lock/park events.
                    frames.csv: per-frame timings in milliseconds. Flags: 1=world loaded, 2=focused, 4=screen open.
                    Only flags=3 enters the primary FPS summary. Blank CPU means that frame was not CPU-timed.
                    timeline.csv: process metrics sampled in the background about once per second. Game context
                    is copied from the game thread; a stale sceneSampleAtSeconds can indicate a stalled game thread.
                    CPU loads are fractions across the process/system's available cores.

                    Phase times are inclusive wall time on the main thread, not pure CPU execution time.
                    'render' includes 'world' and 'hud'; 'world' includes entities, terrain and particles.
                    'present' includes buffer swap and window-event processing. 'fps_wait' is the active FPS cap.
                    Do not add nested phases. Per-frame CPU time is sampled every 16 frames.
                    GPU timestamps are read only after becoming available, every 16 frames, with no glFinish.
                    Queued GPU intervals include submission gaps; they do not measure GPU utilization.
                    A missing hook or unsupported timer is reported as unavailable, not as zero cost.
                    In profile.json, Java/native weights are samples; allocation weights are estimated bytes;
                    blocked weights are nanoseconds. Sampling modes have different periods and must not be added.
                    Lock/park events shorter than 20 ms are not recorded. 'recorder' groups the diagnostic worker
                    and JFR consumer so their work can be separated from normal game workers.

                    Settings and gameplay are never changed automatically. Record separate captures with the same
                    scene/settings for comparison. Use one capture with the unwanted optimization off and another
                    with it on if requested. Keep FPS limits and V-Sync consistent.

                    No raw JFR, chat, tokens, account/player names or UUIDs, server addresses, world names/seeds,
                    coordinates, file paths, environment variables, JVM command line, text/keybind settings,
                    screenshots or raw logs are included. Code class/method names and mod IDs/versions are included.
                    manifest.json contains the SHA-256 and uncompressed size of each other entry.
                    """));
                add(archive, new LinkedHashMap<>(), "manifest.json", writer -> JSON.toJson(Map.of("schema", "arcadium.performance.v1", "files", manifest), writer));
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, target); }
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }

    @FunctionalInterface private interface Content { void write(Writer writer) throws IOException; }
    private static void add(ZipOutputStream archive, Map<String, Object> manifest, String name, Content content) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException missing) { throw new IOException(missing); }
        archive.putNextEntry(new ZipEntry(name));
        long[] bytes = {0};
        OutputStream stream = new OutputStream() {
            @Override public void write(int value) throws IOException { byte[] one = {(byte) value}; write(one, 0, 1); }
            @Override public void write(byte[] value, int offset, int length) throws IOException {
                bytes[0] += length;
                if (bytes[0] > 128L * 1024 * 1024) throw new IOException("Report entry limit exceeded");
                digest.update(value, offset, length); archive.write(value, offset, length);
            }
        };
        Writer writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
        content.write(writer); writer.flush(); archive.closeEntry();
        manifest.put(name, Map.of("bytes", bytes[0], "sha256", HexFormat.of().formatHex(digest.digest())));
    }
}
