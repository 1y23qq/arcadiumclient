package org.polyfrost.oneconfig.internal.ui.themes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.polyfrost.oneconfig.internal.ThemeConfig;

public final class ThemeAccent {
    public record Snapshot(boolean gradient, String preset, boolean custom, int start, int end, int solid, boolean animate, float speed, float smoothness) {
        public Snapshot {
            speed = Float.isFinite(speed) ? Math.max(0, Math.min(5, speed)) : 1;
            smoothness = Float.isFinite(smoothness) ? Math.max(0, Math.min(100, smoothness)) : 100;
        }

        public Snapshot(boolean gradient, String preset, boolean custom, int start, int end, int solid, boolean animate) {
            this(gradient, preset, custom, start, end, solid, animate, 1, 100);
        }

        public int foreground() {
            double low = luminance(gradient ? start : solid);
            double high = luminance(gradient ? end : solid);
            double whiteContrast = 1.05 / (Math.max(low, high) + 0.05);
            double darkContrast = (Math.min(low, high) + 0.05) / (luminance(0xFF10181C) + 0.05);
            return darkContrast >= whiteContrast ? 0xFF10181C : 0xFFFFFFFF;
        }

        public int colorAt(double position, long nanos, boolean animated) {
            return colorAtPhase(position, GradientClock.wrap(nanos / 4_000_000_000.0 * speed), animated);
        }

        public int colorAtPhase(double position, double phase, boolean animated) {
            if (!gradient) return solid;
            double t = (1 - Math.cos((position + (animated && animate ? phase : 0)) * Math.PI * 2)) * 0.5;
            double width = Math.max(0.02, smoothness / 100.0);
            return GradientPresets.mix(start, end, Math.max(0, Math.min(1, (t - 0.5) / width + 0.5)));
        }

        public String json() {
            return String.format(Locale.ROOT,
                "{\"version\":1,\"gradient\":%s,\"preset\":\"%s\",\"custom\":%s,\"start\":\"#%06X\",\"end\":\"#%06X\",\"solid\":\"#%06X\",\"animate\":%s,\"speed\":%.3f,\"smoothness\":%.3f}\n",
                gradient, preset, custom, start & 0xFFFFFF, end & 0xFFFFFF, solid & 0xFFFFFF, animate, speed, smoothness);
        }
    }

    private static volatile Snapshot current = new Snapshot(true, "Arcadium", false, 0xFF3AA0FF, 0xFF36F1B0, 0xFF2B4BFF, true);
    private static volatile boolean animatedChatPrefix = true;
    private static final GradientClock CLOCK = new GradientClock(System.nanoTime(), 1);
    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Arcadium theme sync");
        thread.setDaemon(true);
        return thread;
    });
    private static ScheduledFuture<?> pending;
    private static Snapshot queued;

    private ThemeAccent() { }

    private static double luminance(int color) {
        double r = channel(color >>> 16 & 255), g = channel(color >>> 8 & 255), b = channel(color & 255);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static Snapshot snapshot() { return current; }

    public static boolean animatedChatPrefix() { return animatedChatPrefix; }
    public static void setAnimatedChatPrefix(boolean enabled) { animatedChatPrefix = enabled; }

    public static double phaseAt(long nanos) { return CLOCK.at(nanos); }

    public static void refresh() {
        GradientPresets.Preset preset = GradientPresets.get(ThemeConfig.gradientPreset);
        Snapshot next = new Snapshot(ThemeConfig.gradientAccent, preset.name(), ThemeConfig.customGradient,
            ThemeConfig.customGradient ? ThemeConfig.gradientStart | 0xFF000000 : preset.start(),
            ThemeConfig.customGradient ? ThemeConfig.gradientEnd | 0xFF000000 : preset.end(),
            ThemeConfig.accentColor.getRawArgb(), ThemeConfig.animations, ThemeConfig.gradientSpeed, ThemeConfig.gradientSmoothness);
        CLOCK.change(next.speed(), System.nanoTime(), !next.gradient() || !next.animate());
        current = next;
        publish(next);
    }

    private static synchronized void publish(Snapshot snapshot) {
        String directory = System.getProperty("arcadium.launcherDataDir");
        if (directory == null || directory.isBlank() || snapshot.equals(queued)) return;
        queued = snapshot;
        if (pending != null) pending.cancel(false);
        pending = WRITER.schedule(() -> {
            try {
                write(Path.of(directory).resolve("theme-accent.json"), snapshot);
            } catch (IOException | RuntimeException error) {
                synchronized (ThemeAccent.class) { if (snapshot.equals(queued)) queued = null; }
                LogManager.getLogger("Arcadium/Themes").warn("Could not share the accent with the launcher", error);
            }
        }, 120, TimeUnit.MILLISECONDS);
    }

    static void write(Path target, Snapshot snapshot) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".theme-accent-", ".tmp");
        try {
            Files.writeString(temp, snapshot.json(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
