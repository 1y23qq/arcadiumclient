package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry;
import org.polyfrost.oneconfig.internal.ui.compose.ComposePreloader;
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen;

public final class StartupQa {
    public static final AtomicInteger registryLoads = new AtomicInteger();
    public static final AtomicLong registryLoadNanos = new AtomicLong();
    private static final LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    private static int titleFrames;
    private static int optionsFrames;
    private static long titleUptime, optionsOpened;
    private static boolean done;

    private StartupQa() { }

    public static boolean enabled() { return System.getProperty("arcadium.startupQa.output") != null; }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!"ArcadiumTest".equals(mc.getSession().getUsername()) || !"0".equals(mc.getSession().getAccessToken()))
            throw new IllegalStateException("Startup verification requires an isolated offline account");
        OneConfigConfig.limitUnfocusedFps = false;
        mc.options.pauseOnUnfocus = false;
    }

    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (done) return;
        try {
            if (titleUptime == 0) {
                if (!(mc.screen instanceof TitleScreen || mc.screen instanceof ArcadiumMainMenu) || ++titleFrames < 30) return;
                titleUptime = ManagementFactory.getRuntimeMXBean().getUptime();
                result.put("uptimeMillis", titleUptime);
                result.put("renderedTitleFrames", titleFrames);
                result.put("screen", mc.screen.getClass().getName());
                result.put("heapUsedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
                result.put("loadedClasses", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
                result.put("forgePrepared", Boolean.getBoolean("arcadium.forge.prepared"));
                result.put("processId", ProcessHandle.current().pid());
            }
            if (Boolean.getBoolean("arcadium.startupQa.checkOptions")) {
                if (optionsOpened == 0) {
                    if (!ComposePreloader.INSTANCE.getStopped()) return;
                    var failed = ComposePreloader.class.getDeclaredField("failed");
                    failed.setAccessible(true);
                    if (failed.getBoolean(ComposePreloader.INSTANCE)) throw new IllegalStateException("UI prewarm failed");
                    result.put("prewarmReadyUptimeMillis", ManagementFactory.getRuntimeMXBean().getUptime());
                    result.put("registryLoadsBeforeOptions", registryLoads.get());
                    result.put("registryLoadMillisBeforeOptions", registryLoadNanos.get() / 1_000_000d);
                    result.put("configCount", ConfigRegistry.INSTANCE.getConfigs().size());
                    optionsOpened = System.nanoTime();
                    mc.openScreen(OneConfigUIScreen.open());
                    result.put("optionsInitMillis", (System.nanoTime() - optionsOpened) / 1_000_000d);
                    return;
                }
                if (!(mc.screen instanceof OneConfigUIScreen)) throw new IllegalStateException("Options screen did not open");
                if (++optionsFrames < 3) return;
                result.put("optionsRenderedMillis", (System.nanoTime() - optionsOpened) / 1_000_000d);
                result.put("optionsRenderedFrames", optionsFrames);
                result.put("registryLoadsAfterOptions", registryLoads.get());
            }
            result.put("passed", true);
            Files.writeString(Path.of(System.getProperty("arcadium.startupQa.output")), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            done = true;
            mc.stop();
        } catch (Exception error) { throw new IllegalStateException("Startup verification failed", error); }
    }
}
