// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.arcadiumclient.modern.client.features.CustomCrosshair;
import io.github.arcadiumclient.modern.client.features.KillEffectsConfig;
import io.github.arcadiumclient.modern.client.features.LightLevelConfig;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;

final class ModernBenchmarkQa {
    private final Minecraft minecraft;
    private final Path output;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> cases = new ArrayList<>();
    private final Map<Hud, Boolean> hidden = new IdentityHashMap<>();
    private final boolean crosshair = CustomCrosshair.INSTANCE.enabled();
    private final boolean light = LightLevelConfig.getEnabled();
    private final boolean kills = KillEffectsConfig.getEnabled();
    private final int previousCap;
    private final boolean previousVsync;
    private final int seconds = Math.clamp(Integer.getInteger("arcadium.guiQa.benchmarkSeconds", 15), 5, 30);
    private final long started = System.nanoTime();
    private long phaseStarted = started;
    private int phase;
    private int chunkCount;
    private long chunkChanged = started;
    private boolean complete;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        var test = new ModernBenchmarkQa(minecraft, output);
        test.start();
        return test.completion;
    }

    private ModernBenchmarkQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft;
        this.output = output;
        previousCap = minecraft.options.framerateLimit().get();
        previousVsync = minecraft.options.enableVsync().get();
        for (Hud hud : HudManager.INSTANCE.getActiveInstances()) {
            String type = hud.getClass().getName();
            if (!type.startsWith("org.polyfrost.vanillahud.") && !type.equals("org.polyfrost.chatting.hud.ChatWindowHud") &&
                    !type.equals("org.polyfrost.oneconfig.api.hud.v1.OneConfigHudCompat")) hidden.put(hud, hud.getHidden());
        }
    }

    private void start() {
        try {
            minecraft.setScreen(null);
            minecraft.options.framerateLimit().set(260);
            minecraft.options.enableVsync().set(false);
            minecraft.getFramerateLimitTracker().setFramerateLimit(260);
            minecraft.getWindow().updateVsync(false);
            minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            minecraft.player.setYRot(45);
            minecraft.player.setXRot(15);
            report.put("warmupInput", NativeInputQa.pulse(minecraft));
            report.put("minecraft", "1.21.11");
            report.put("secondsPerCase", seconds);
            report.put("world", "Synthetic creative superflat seed 16438450; no public server");
            report.put("scope", "Configured Arcadium informational HUDs, custom crosshair, Light Level and Kill Effects versus those decorations disabled. Native VanillaHUD, ChatWindow and compat wrappers remain unchanged, as do bundled rendering optimizers. No kill effect is triggered in this idle scene.");
            report.put("configuredHuds", hidden.entrySet().stream().map(entry -> Map.of("class", entry.getKey().getClass().getName(), "id", entry.getKey().getTree().getID(), "hidden", entry.getValue())).toList());
            report.put("preservedNativeHuds", HudManager.INSTANCE.getActiveInstances().stream().filter(hud -> !hidden.containsKey(hud)).map(hud -> Map.of("class", hud.getClass().getName(), "id", hud.getTree().getID(), "hidden", hud.getHidden())).toList());
            report.put("configuredEffects", Map.of("customCrosshair", crosshair, "lightLevel", light, "killEffects", kills));
            report.put("renderer", RenderSystem.getDevice().getRenderer());
            report.put("vendor", RenderSystem.getDevice().getVendor());
            report.put("backend", RenderSystem.getDevice().getBackendName());
            report.put("java", System.getProperty("java.runtime.version"));
            report.put("processors", Runtime.getRuntime().availableProcessors());
            report.put("processorIdentifier", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unavailable"));
            report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            report.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(value -> !value.toLowerCase().contains("token")).toList());
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
            progress();
        } catch (Throwable failure) { finish(failure); }
    }

    private void tick() throws Exception {
        long now = System.nanoTime();
        if (now - started > 150_000_000_000L) throw new IllegalStateException("Benchmark timed out in phase " + phase);
        if (minecraft.level == null || minecraft.player == null) throw new IllegalStateException("Benchmark lost its synthetic world");
        minecraft.player.setYRot(45);
        minecraft.player.setXRot(15);
        long elapsed = now - phaseStarted;
        switch (phase) {
            case 0 -> {
                int loaded = minecraft.level.getChunkSource().getLoadedChunksCount();
                if (loaded != chunkCount) { chunkCount = loaded; chunkChanged = now; }
                if (elapsed < 20_000_000_000L || now - chunkChanged < 3_000_000_000L) return;
                require(minecraft.isWindowActive(), "Benchmark requires a focused game window");
                report.put("configuredCaseInput", NativeInputQa.pulse(minecraft));
                require(minecraft.getFramerateLimitTracker().getThrottleReason().name().equals("NONE") && minecraft.getFramerateLimitTracker().getFramerateLimit() >= 260, "Benchmark is still limited by a native idle policy or frame cap");
                report.put("warmupMs", elapsed / 1_000_000L);
                report.put("loadedChunks", loaded);
                report.put("framebuffer", List.of(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()));
                report.put("window", List.of(minecraft.getWindow().getScreenWidth(), minecraft.getWindow().getScreenHeight()));
                report.put("renderDistance", minecraft.options.renderDistance().get());
                report.put("simulationDistance", minecraft.options.simulationDistance().get());
                report.put("framerateOption", minecraft.options.framerateLimit().get());
                report.put("actualFramerateLimit", minecraft.getFramerateLimitTracker().getFramerateLimit());
                report.put("throttleReason", minecraft.getFramerateLimitTracker().getThrottleReason().name());
                report.put("vsync", minecraft.options.enableVsync().get());
                report.put("camera", List.of(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), minecraft.player.getYRot(), minecraft.player.getXRot()));
                ModernFrameSampler.start();
                next();
            }
            case 1 -> {
                if (elapsed < seconds * 1_000_000_000L) return;
                recordCase("configured", elapsed);
                for (Hud hud : hidden.keySet()) hud.setHidden(true);
                CustomCrosshair.INSTANCE.setEnabled(false);
                LightLevelConfig.setEnabled(false);
                KillEffectsConfig.setEnabled(false);
                next();
            }
            case 2 -> {
                if (elapsed < 5_000_000_000L) return;
                report.put("disabledCaseInput", NativeInputQa.pulse(minecraft));
                require(minecraft.getFramerateLimitTracker().getThrottleReason().name().equals("NONE") && minecraft.getFramerateLimitTracker().getFramerateLimit() >= 260, "Second benchmark case is still limited by a native idle policy or frame cap");
                ModernFrameSampler.start();
                next();
            }
            case 3 -> {
                if (elapsed < seconds * 1_000_000_000L) return;
                recordCase("decorations-disabled", elapsed);
                finish(null);
            }
            default -> throw new IllegalStateException("Unknown benchmark phase " + phase);
        }
    }

    private void recordCase(String label, long elapsed) {
        var data = new LinkedHashMap<>(ModernFrameSampler.finish());
        data.put("case", label);
        data.put("elapsedMs", elapsed / 1_000_000d);
        data.put("windowActive", minecraft.isWindowActive());
        data.put("loadedChunks", minecraft.level.getChunkSource().getLoadedChunksCount());
        data.putAll(NativeInputQa.state(minecraft));
        data.put("actualFramerateLimit", minecraft.getFramerateLimitTracker().getFramerateLimit());
        cases.add(data);
        require(((Number) data.get("samples")).intValue() >= 100, "Benchmark recorded fewer than 100 real frames");
        require(((Number) data.get("inactiveOrOverlayFrames")).intValue() == 0, "Benchmark window lost focus or opened an overlay during measurement");
        require(((Number) data.get("overflowFrames")).intValue() == 0, "Benchmark frame sample buffer overflowed");
        require(data.get("nativeThrottleReason").equals("NONE") && (int) data.get("actualFramerateLimit") >= 260, "Benchmark case ended under a native idle/frame cap");
    }

    private void next() throws Exception {
        phase++;
        phaseStarted = System.nanoTime();
        progress();
    }

    private void progress() throws Exception {
        report.put("phase", phase);
        report.put("elapsedMs", (System.nanoTime() - started) / 1_000_000L);
        report.put("cases", cases);
        Files.writeString(output.resolve("benchmark-progress.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private void finish(Throwable failure) {
        if (complete) return;
        complete = true;
        ModernFrameSampler.finish();
        try {
            for (var entry : hidden.entrySet()) entry.getKey().setHidden(entry.getValue());
            CustomCrosshair.INSTANCE.setEnabled(crosshair);
            LightLevelConfig.setEnabled(light);
            KillEffectsConfig.setEnabled(kills);
            minecraft.options.framerateLimit().set(previousCap);
            minecraft.options.enableVsync().set(previousVsync);
            minecraft.getFramerateLimitTracker().setFramerateLimit(previousCap);
            minecraft.getWindow().updateVsync(previousVsync);
            report.put("cases", cases);
            report.put("passed", failure == null);
            report.put("restored", true);
            if (failure != null) report.put("error", failure.toString());
            Files.writeString(output.resolve("benchmark.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (failure == null) completion.complete(report);
            else completion.completeExceptionally(failure);
        } catch (Throwable restoreFailure) { completion.completeExceptionally(restoreFailure); }
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
