// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.diagnostics;

import java.awt.Desktop;
import java.lang.management.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.text.LiteralText;
import org.lwjgl.opengl.Display;
import org.polyfrost.oneconfig.internal.ui.api.*;

public final class PerformanceCapture {
    private static final ScheduledExecutorService WORKER = worker();
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile Session active;
    private static volatile PerformanceCaptureStatus status = new PerformanceCaptureStatus("Ready", "Record the scene you want to investigate. Send the resulting ZIP to the developer.", 0, 60, null, false);
    private static volatile Path reports;

    private PerformanceCapture() { }

    private static ScheduledExecutorService worker() {
        var executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "Arcadium performance report"); thread.setDaemon(true); return thread;
        });
        executor.setRemoveOnCancelPolicy(true); return executor;
    }

    public static void initialize() {
        reports = Minecraft.getInstance().gameDir.toPath().resolve("arcadium/diagnostics").toAbsolutePath();
        PerformanceCapturePage.setController(new PerformanceCaptureController() {
            public PerformanceCaptureStatus snapshot() { return status; }
            public void start(int seconds) { request(seconds); }
            public void stop() { finish(active, "manual", false); }
            public void cancel() { finish(active, "cancelled", true); }
            public void openFolder() {
                WORKER.execute(() -> {
                    try { Files.createDirectories(reports); Desktop.getDesktop().open(reports.toFile()); }
                    catch (Exception error) { if (!BUSY.get()) update("Could not open folder", "Reports are saved in the game's arcadium/diagnostics folder.", 0, 60, null, false); }
                });
            }
        });
    }

    private static void request(int seconds) {
        if (!List.of(30, 60, 120).contains(seconds) || !BUSY.compareAndSet(false, true)) return;
        update("Preparing", "Preparing the recorder. This can take a few seconds.", 0, seconds, null, true);
        Minecraft.getInstance().executeTask(() -> {
            Session session = null;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.world == null || mc.player == null) {
                    BUSY.set(false); update("Join a world first", "Open this page again in the scene where performance is poor.", 0, seconds, null, false); return;
                }
                Session previous = active;
                if (previous != null && previous.gpu != null) previous.gpu.close();
                session = new Session(seconds, Thread.currentThread().threadId(), CaptureContext.environment(mc), CaptureContext.settings(mc), CaptureContext.counters(), mc.world);
                active = session;
                session.lastOptimizationState = CaptureContext.optimizationState();
                mc.openScreen(null);
                notice("Performance recording starts after preparation and a 5-second warmup. Play normally; a ZIP will be saved automatically.");
                Session value = session;
                WORKER.execute(() -> prepare(value));
            } catch (Throwable error) {
                if (session != null) finish(session, "setup_failed", true);
                else { BUSY.set(false); update("Could not start", "The recorder could not initialize (" + error.getClass().getSimpleName() + ").", 0, seconds, null, false); }
            }
        });
    }

    private static void prepare(Session session) {
        try {
            session.environment.put("buildFingerprints", CaptureContext.fingerprints());
            session.data = new CaptureData(session.environment, session.lastSettings, session.lastCounters);
            session.profile = new CaptureProfile(session.renderThread); session.profile.open();
            if (session.finishing.get()) { session.profile.close(); return; }
            session.gpu = new CaptureGpu(session.data);
            session.phase = 1;
            update("Starting in 5 seconds", "Play normally. Settings are not changed by the recorder.", 0, session.seconds, null, true);
            WORKER.schedule(() -> begin(session), 5, TimeUnit.SECONDS);
        } catch (Throwable failure) {
            finish(session, "setup_failed", true);
        }
    }

    private static void begin(Session session) {
        if (session.finishing.get()) return;
        session.data.settings = Map.copyOf(session.lastSettings); session.data.countersBefore = Map.copyOf(session.lastCounters);
        session.recordedSettings = session.lastSettings;
        session.data.optimizationStateAtStart = session.lastOptimizationState;
        session.started = System.nanoTime(); session.startedAt = Instant.now(); session.profile.begin(session.startedAt); session.phase = 2;
        update("Recording", "Play normally. You can finish early or cancel from Diagnostics.", 0, session.seconds, null, true);
        session.progress = WORKER.scheduleAtFixedRate(() -> {
            if (!session.finishing.get()) {
                update("Recording", "Play normally. The report will be saved automatically.",
                    (int) Math.min(session.seconds, (System.nanoTime() - session.started) / 1_000_000_000L), session.seconds, null, true);
                try {
                    Map<String, Object> point = new LinkedHashMap<>(session.lastScene);
                    point.put("sceneSampleAtSeconds", point.getOrDefault("atSeconds", -1));
                    point.putAll(CaptureContext.process(System.nanoTime() - session.started, session.renderThread));
                    session.data.point(point);
                } catch (RuntimeException unavailable) { session.data.processSamplesFailed++; }
            }
        }, 1, 1, TimeUnit.SECONDS);
        session.deadline = WORKER.schedule(() -> finish(session, "duration", false), session.seconds, TimeUnit.SECONDS);
    }

    public static void frameBegin() {
        Session session = active;
        if (session == null) return;
        if (session.finishing.get()) {
            if (session.gpu != null) session.gpu.close();
            session.frameOpen = false;
            if (!BUSY.get() && active == session) active = null;
            return;
        }
        if (session.phase == 0 || session.data == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            long now = System.nanoTime();
            if (!session.gpuInitialized && session.gpu != null) { session.gpu.initialize(); session.gpuInitialized = true; }
            if (now - session.lastContext >= 1_000_000_000L) {
                long before = System.nanoTime();
                session.lastSettings = CaptureContext.settings(mc); session.lastCounters = CaptureContext.counters();
                session.lastOptimizationState = CaptureContext.optimizationState();
                if (session.phase == 2) {
                    session.lastScene = CaptureContext.scene(mc, now - session.started);
                    if (!session.lastSettings.equals(session.recordedSettings)) {
                        if (session.recordedSettings != null) session.data.change(now - session.started, session.lastSettings);
                        session.recordedSettings = session.lastSettings;
                    }
                    session.data.countersAfter = session.lastCounters;
                    session.data.optimizationStateLatest = session.lastOptimizationState;
                }
                if (session.phase == 2) session.data.contextCollectionNs += System.nanoTime() - before;
                session.lastContext = now;
            }
            if (session.phase != 2) return;
            if (mc.world != session.world) { session.data.worldChanges++; session.world = mc.world; }
            session.frameFlags = (mc.world != null ? CaptureData.WORLD : 0) | (Display.isActive() ? CaptureData.FOCUSED : 0) | (mc.screen != null ? CaptureData.SCREEN : 0);
            session.frameStarted = System.nanoTime();
            Arrays.fill(session.phaseTimes, 0); Arrays.fill(session.phaseStarts, 0); Arrays.fill(session.phaseHits, 0);
            session.cpuStarted = (++session.frames & 15) == 0 ? cpuTime() : -1;
            session.frameOpen = true;
            session.gpu.frame(session.frameStarted - session.started);
        } catch (Throwable error) { finish(session, "collector_failed", false); }
    }

    public static void frameEnd() {
        Session session = active;
        if (session == null || !session.frameOpen) return;
        session.frameOpen = false;
        if (session.finishing.get()) return;
        long now = System.nanoTime();
        long cpu = session.cpuStarted < 0 ? -1 : cpuTime();
        boolean transition = session.previousFlags != session.frameFlags;
        for (long started : session.phaseStarts) if (started != 0) { session.data.incompleteFrames++; break; }
        if (session.previousTail != 0 && !transition) session.data.frame(now - session.started, now - session.previousTail,
            session.frameFlags, session.phaseTimes, session.phaseHits, cpu < 0 ? -1 : Math.max(0, cpu - session.cpuStarted), now - session.frameStarted);
        else session.data.incompleteFrames++;
        session.previousFlags = session.frameFlags; session.previousTail = now;
    }

    public static void phaseBegin(int phase) {
        Session session = active;
        if (session == null || !session.frameOpen || session.finishing.get()) return;
        session.phaseStarts[phase] = System.nanoTime(); session.phaseHits[phase]++;
        if (phase == 1) session.gpu.beginRender();
        if (phase == 7) session.gpu.beforePresent();
    }

    public static void phaseEnd(int phase) {
        Session session = active;
        if (session == null || !session.frameOpen || session.finishing.get()) return;
        long before = session.phaseStarts[phase];
        if (before != 0) { session.phaseTimes[phase] += System.nanoTime() - before; session.phaseStarts[phase] = 0; }
    }

    private static void finish(Session session, String reason, boolean discard) {
        if (session == null || !session.finishing.compareAndSet(false, true)) return;
        if (session.progress != null) session.progress.cancel(false);
        if (session.deadline != null) session.deadline.cancel(false);
        long duration = session.started == 0 ? 0 : System.nanoTime() - session.started;
        if (session.profile != null) session.profile.end(session.startedAt == null ? Instant.now() : session.startedAt.plusNanos(duration));
        if (session.data != null) session.data.close(duration, reason);
        update(discard ? "Cancelling" : "Saving report", "Finalizing the recorder in the background.", (int) (duration / 1_000_000_000L), session.seconds, null, true);
        WORKER.execute(() -> {
            try {
                if (session.profile != null) session.profile.finish();
                if (discard || session.data == null || session.started == 0) {
                    update("Cancelled", "No report was saved.", 0, session.seconds, null, false); return;
                }
                Path archive = CaptureArchive.write(reports, session.data, session.profile, session.seconds);
                update("Report saved", "Send this ZIP to the developer. Its location is the game's arcadium/diagnostics folder.", (int) (duration / 1_000_000_000L), session.seconds, archive.getFileName().toString(), false);
                Minecraft.getInstance().executeTask(() -> notice("Performance report saved: " + archive.getFileName() + ". Open Optimizations (Beta) > Diagnostics to find it."));
            } catch (Throwable error) {
                update("Could not save report", "Check free disk space and folder permissions (" + error.getClass().getSimpleName() + ").", 0, session.seconds, null, false);
            } finally {
                if (session.profile != null) session.profile.close();
                BUSY.set(false); session.completed.countDown();
            }
        });
    }

    public static void shutdown() {
        Session session = active;
        if (session == null) return;
        finish(session, "game_exit", false);
        try { session.completed.await(3, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    private static void notice(String text) {
        var mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.getChat().addMessage(new LiteralText("Arcadium > " + text));
    }
    private static long cpuTime() {
        try { return THREADS.isCurrentThreadCpuTimeSupported() && THREADS.isThreadCpuTimeEnabled() ? THREADS.getCurrentThreadCpuTime() : -1; }
        catch (UnsupportedOperationException ignored) { return -1; }
    }
    private static void update(String state, String detail, int elapsed, int duration, String file, boolean busy) {
        status = new PerformanceCaptureStatus(state, detail, elapsed, duration, file, busy);
    }

    private static final class Session {
        final int seconds; final long renderThread;
        final Map<String, Object> environment;
        volatile Map<String, Object> lastSettings;
        volatile Map<String, Long> lastCounters;
        volatile Map<String, Object> lastScene = Map.of();
        volatile Map<String, Object> lastOptimizationState = Map.of();
        Map<String, Object> recordedSettings;
        final AtomicBoolean finishing = new AtomicBoolean();
        final CountDownLatch completed = new CountDownLatch(1);
        volatile int phase;
        volatile long started;
        Instant startedAt;
        volatile CaptureData data;
        volatile CaptureProfile profile;
        volatile CaptureGpu gpu;
        ScheduledFuture<?> progress, deadline;
        final long[] phaseTimes = new long[CaptureData.PHASES.length], phaseStarts = new long[CaptureData.PHASES.length], phaseHits = new long[CaptureData.PHASES.length];
        Object world;
        boolean frameOpen, gpuInitialized;
        int frames, frameFlags, previousFlags;
        long frameStarted, previousTail, cpuStarted, lastContext;
        Session(int seconds, long thread, Map<String, Object> environment, Map<String, Object> settings, Map<String, Long> counters, Object world) {
            this.seconds = seconds; renderThread = thread; this.environment = environment; lastSettings = settings; lastCounters = counters; this.world = world;
        }
    }
}
