// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

final class ModernIdleQa {
    private final Minecraft minecraft;
    private final Path output;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<Path, byte[]> files = new LinkedHashMap<>();
    private final boolean ownEnabled = OneConfigConfig.limitUnfocusedFps;
    private final int ownLimit = OneConfigConfig.unfocusedFps;
    private final int nativeLimit;
    private final boolean pauseOnLostFocus;
    private Class<?> modType;
    private Object config, focused, unfocused, observer;
    private boolean enabled, wasFocused, wasHovered, wasIconified;
    private int unfocusedLimit;
    private float unfocusedVolume;
    private int phase, ticks;
    private boolean complete;
    private boolean snapshotsCaptured;
    private final long started = System.nanoTime();

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        var qa = new ModernIdleQa(minecraft, output);
        qa.start();
        return qa.completion;
    }

    private ModernIdleQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft; this.output = output;
        nativeLimit = minecraft.options.framerateLimit().get(); pauseOnLostFocus = minecraft.options.pauseOnLostFocus;
    }

    private void start() {
        try {
            require(FabricLoader.getInstance().isModLoaded("dynamic_fps"), "Selected Dynamic FPS component is not loaded");
            modType = Class.forName("dynamic_fps.impl.DynamicFPSMod");
            Class<?> type = Class.forName("dynamic_fps.impl.config.DynamicFPSConfig");
            Class<?> states = Class.forName("dynamic_fps.impl.PowerState");
            config = type.getField("INSTANCE").get(null);
            enabled = (boolean) call(config, "enabled");
            focused = type.getMethod("get", states).invoke(config, states.getField("FOCUSED").get(null));
            unfocused = type.getMethod("get", states).invoke(config, states.getField("UNFOCUSED").get(null));
            report.put("focusedNativeDefaultCap", call(focused, "frameRateTarget"));
            unfocusedLimit = (int) call(unfocused, "frameRateTarget");
            unfocusedVolume = (float) unfocused.getClass().getMethod("volumeMultiplier", SoundSource.class).invoke(unfocused, SoundSource.MASTER);
            observer = modType.getMethod("getWindow").invoke(null);
            require(observer != null, "Native Dynamic FPS window observer is absent");
            wasFocused = (boolean) call(observer, "isFocused"); wasHovered = (boolean) call(observer, "isHovered"); wasIconified = (boolean) call(observer, "isIconified");
            for (Path path : List.of(FabricLoader.getInstance().getConfigDir().resolve("dynamic_fps.json"), ConfigManager.active().getFolder().resolve("dynamic_fps.json"))) {
                Path absolute = path.toAbsolutePath().normalize();
                if (!files.containsKey(absolute)) files.put(absolute, Files.exists(absolute) ? Files.readAllBytes(absolute) : null);
            }
            snapshotsCaptured = true;
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.framerateLimit().set(180);
            config.getClass().getMethod("setEnabled", boolean.class).invoke(config, true);
            profile(unfocused, 37, .25f);
            OneConfigConfig.limitUnfocusedFps = true; OneConfigConfig.unfocusedFps = 5;
            callback("onIconifyChanged", false); callback("onMouseChanged", false); callback("onFocusChanged", true);
            apply();
            report.put("nativeInput", NativeInputQa.pulse(minecraft));
            report.put("inputPath", "Actual registered WindowObserver callback handlers, chaining the native Minecraft callbacks; no GLFW hardware polling override");
            report.put("focusedConfigContract", "FOCUSED returns read-only Config.ACTIVE with no saved PowerState. Only the saved UNFOCUSED profile is edited.");
            report.put("previousEnabled", enabled);
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        } catch (Throwable failure) { finish(failure); }
    }

    private void tick() throws Exception {
        require(System.nanoTime() - started < 35_000_000_000L, "Native idle QA timed out");
        if (++ticks < 8) return;
        switch (phase) {
            case 0 -> {
                var state = sample(); report.put("focused", state);
                require(minecraft.isWindowActive() && state.get("powerState").equals("FOCUSED") && (int) state.get("nativeCap") == 180, "Focused FPS was limited by the unfocused preference");
                callback("onFocusChanged", false); apply(); next();
            }
            case 1 -> {
                var state = sample(); report.put("unfocusedOwnLimit", state);
                require(!minecraft.isWindowActive() && state.get("powerState").equals("UNFOCUSED") && (int) state.get("nativeCap") == 5, "Arcadium did not clamp the actual native/Dynamic FPS result to5");
                require(Math.abs((float) state.get("targetMasterGain") - .25f) < .001, "Unfocused volume profile was not selected");
                OneConfigConfig.limitUnfocusedFps = false; next();
            }
            case 2 -> {
                var state = sample(); report.put("unfocusedNativeLimit", state);
                require((int) state.get("nativeCap") == 37, "Turning off Arcadium Unfocused FPS discarded the native Dynamic FPS cap");
                config.getClass().getMethod("setEnabled", boolean.class).invoke(config, false); apply(); next();
            }
            case 3 -> {
                var state = sample(); report.put("bothLimitsDisabled", state);
                require((int) state.get("nativeCap") == 180, "Disabling both idle controls did not restore the native max FPS option");
                finish(null);
            }
            default -> throw new IllegalStateException("Unknown idle QA phase " + phase);
        }
    }

    private Map<String, Object> sample() throws Exception {
        var value = new LinkedHashMap<String, Object>();
        value.put("powerState", modType.getMethod("powerState").invoke(null).toString());
        value.put("nativeCap", minecraft.getFramerateLimitTracker().getFramerateLimit());
        value.put("dynamicTargetCap", modType.getMethod("targetFrameRate").invoke(null));
        value.put("targetMasterGain", modType.getMethod("volumeMultiplier", SoundSource.class).invoke(null, SoundSource.MASTER));
        value.put("interpolatedMasterGain", Class.forName("dynamic_fps.impl.feature.volume.SmoothVolumeHandler").getMethod("volumeMultiplier", SoundSource.class).invoke(null, SoundSource.MASTER));
        value.put("nativeWindowActive", minecraft.isWindowActive());
        value.put("observerFocused", call(observer, "isFocused")); value.put("observerHovered", call(observer, "isHovered"));
        value.put("observerIconified", call(observer, "isIconified"));
        value.putAll(NativeInputQa.state(minecraft));
        return value;
    }

    private void profile(Object profile, int cap, float volume) throws Exception {
        profile.getClass().getMethod("setFrameRateTarget", int.class).invoke(profile, cap);
        profile.getClass().getMethod("setVolumeMultiplier", SoundSource.class, float.class).invoke(profile, SoundSource.MASTER, volume);
    }

    private void apply() throws Exception { modType.getMethod("onConfigChanged").invoke(null); }
    private void callback(String name, boolean value) throws Exception {
        Method method = observer.getClass().getDeclaredMethod(name, long.class, boolean.class); method.setAccessible(true);
        method.invoke(observer, minecraft.getWindow().handle(), value);
    }
    private static Object call(Object owner, String method) throws Exception { return owner.getClass().getMethod(method).invoke(owner); }
    private void next() { phase++; ticks = 0; }

    private void finish(Throwable failure) {
        if (complete) return;
        complete = true;
        try {
            if (snapshotsCaptured) {
                profile(unfocused, unfocusedLimit, unfocusedVolume);
                config.getClass().getMethod("setEnabled", boolean.class).invoke(config, enabled);
                callback("onIconifyChanged", wasIconified); callback("onMouseChanged", wasHovered); callback("onFocusChanged", wasFocused); apply();
            }
            OneConfigConfig.limitUnfocusedFps = ownEnabled; OneConfigConfig.unfocusedFps = ownLimit;
            minecraft.options.framerateLimit().set(nativeLimit); minecraft.options.pauseOnLostFocus = pauseOnLostFocus;
            for (var entry : files.entrySet()) {
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                else Files.write(entry.getKey(), entry.getValue());
            }
            report.put("passed", failure == null); report.put("restored", true);
            if (failure != null) report.put("error", failure.toString());
            Files.writeString(output.resolve("idle.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
        } catch (Throwable restoreFailure) { completion.completeExceptionally(restoreFailure); }
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
