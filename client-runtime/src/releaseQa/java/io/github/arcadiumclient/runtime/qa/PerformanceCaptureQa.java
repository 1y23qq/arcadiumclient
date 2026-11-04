// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.polyfrost.oneconfig.internal.ui.api.PerformanceCapturePage;
import org.polyfrost.oneconfig.internal.ui.api.PerformanceCaptureController;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.ModsRoute;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;

public final class PerformanceCaptureQa {
    private static int ticks, phase, wait, mode, pass;
    private static boolean done, changed;
    private static final Map<String, Object> report = new LinkedHashMap<>(), originalBeta = new TreeMap<>();
    private static final List<String> archives = new ArrayList<>();
    private static PerformanceCaptureController controller;
    private PerformanceCaptureQa() { }

    public static void tick() {
        if (done || System.getProperty("arcadium.performanceCapture.output") == null) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use an isolated offline fixture");
            require(++ticks < 3600, "Native diagnostic QA timed out at " + phase);
            wait++;
            if (wait < 8) return;
            switch (phase) {
                case 0 -> {
                    if (ticks < 65 || mc.screen == null) return;
                    controller = PerformanceCapturePage.getController(); require(controller != null, "Production recorder not registered");
                    ClientPreferences.setSetupComplete(true); OneConfigConfig.limitUnfocusedFps = false;
                    mc.options.vsync = false; Display.setVSyncEnabled(false); mc.options.fpsLimit = 120;
                    for (var entry : BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet())
                        if (entry.getValue() instanceof org.polyfrost.oneconfig.api.config.v1.Property<?> property) originalBeta.put(entry.getKey(), property.get());
                    open(); next(1);
                }
                case 1 -> { if (node("Optimizations (Beta)") == null) return; click(node("Optimizations (Beta)")); next(2); }
                case 2 -> { Object button = description("Open optimization diagnostics"); if (button == null) return; click(button); next(3); }
                case 3 -> {
                    if (node("Performance report") == null) return;
                    screenshot("diagnostics-ready"); click(node("Record")); next(4);
                }
                case 4 -> {
                    if (!controller.snapshot().getState().equals("Join a world first")) return;
                    require(!controller.snapshot().getBusy(), "No-world start left the recorder locked");
                    report.put("noWorldStartRejected", true); mc.openScreen(null);
                    mc.startGame("arcadium-performance-report-qa", "Private fixture world", new WorldSettings(67812, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands()); next(5);
                }
                case 5 -> {
                    if (mc.world == null || mc.player == null || wait < 120) return;
                    mc.openScreen(null); open(); next(6);
                }
                case 6 -> { if (node("Optimizations (Beta)") == null) return; click(node("Optimizations (Beta)")); next(7); }
                case 7 -> { Object button = description("Open optimization diagnostics"); if (button == null) return; click(button); next(8); }
                case 8 -> {
                    if (node("Performance report") == null) return;
                    click(node("30 s")); screenshot("diagnostics-before-record"); click(node("Record")); next(9);
                }
                case 9 -> {
                    if (!controller.snapshot().getState().equals("Recording")) return;
                    require(mc.screen == null, "Record did not close the actual game menu");
                    require(controller.snapshot().getDurationSeconds() == 30, "GUI duration selection did not reach the recorder");
                    if (controller.snapshot().getElapsedSeconds() < 3) return;
                    open(); next(10);
                }
                case 10 -> { if (node("Optimizations (Beta)") == null) return; click(node("Optimizations (Beta)")); next(11); }
                case 11 -> { Object button = description("Open optimization diagnostics"); if (button == null) return; click(button); next(12); }
                case 12 -> {
                    if (node("Finish and save") == null) return;
                    screenshot("diagnostics-recording"); mc.options.fpsLimit = 90; changed = true; next(13);
                }
                case 13 -> { if (wait < 50) return; mc.openScreen(null); next(14); }
                case 14 -> {
                    if (!controller.snapshot().getState().equals("Report saved")) return;
                    require(changed && mc.options.fpsLimit == 90, "Recorder changed the user-selected FPS cap");
                    archives.add(controller.snapshot().getArchiveName()); validate(archives.getFirst(), "duration");
                    report.put("naturalCompletion", true); mode = 1; open(); next(15);
                }
                case 15 -> { if (node("Optimizations (Beta)") == null) return; click(node("Optimizations (Beta)")); next(16); }
                case 16 -> { Object button = description("Open optimization diagnostics"); if (button == null) return; click(button); next(17); }
                case 17 -> {
                    if (node("Report saved") == null) return;
                    screenshot("diagnostics-saved-simple"); click(node("Record")); next(18);
                }
                case 18 -> {
                    if (!controller.snapshot().getState().equals("Recording") || controller.snapshot().getElapsedSeconds() < 2) return;
                    open(); next(19);
                }
                case 19 -> { if (node("Optimizations (Beta)") == null) return; click(node("Optimizations (Beta)")); next(20); }
                case 20 -> { Object button = description("Open optimization diagnostics"); if (button == null) return; click(button); next(21); }
                case 21 -> { if (node("Cancel") == null) return; click(node("Cancel")); next(22); }
                case 22 -> {
                    if (!controller.snapshot().getState().equals("Cancelled") || node("Record") == null) return;
                    require(countArchives() == 1, "Cancellation exported a report"); report.put("cancellationDiscarded", true);
                    click(node("Record")); next(23);
                }
                case 23 -> {
                    if (!controller.snapshot().getState().equals("Recording") || controller.snapshot().getElapsedSeconds() < 3) return;
                    open(); next(24);
                }
                case 24 -> { if (node("Optimizations (Beta)") == null) return; click(node("Optimizations (Beta)")); next(25); }
                case 25 -> { Object button = description("Open optimization diagnostics"); if (button == null) return; click(button); next(26); }
                case 26 -> { if (node("Finish and save") == null) return; click(node("Finish and save")); next(27); }
                case 27 -> {
                    if (!controller.snapshot().getState().equals("Report saved") || node("Report saved") == null) return;
                    archives.add(controller.snapshot().getArchiveName()); validate(archives.getLast(), "manual");
                    require(countArchives() == 2, "Repeated recording did not create unique archives");
                    for (var entry : originalBeta.entrySet()) require(Objects.equals(entry.getValue(), BetaOptimizationsConfig.INSTANCE.getTree().getProp(entry.getKey()).get()), "Recorder changed Beta option " + entry.getKey());
                    report.put("manualCompletion", true); report.put("settingsPreserved", true); report.put("guiModes", List.of("Modern", "Simple"));
                    screenshot("diagnostics-final"); finish(null);
                }
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private static Path directory() { return Minecraft.getInstance().gameDir.toPath().resolve("arcadium/diagnostics"); }
    private static long countArchives() throws Exception { try (var files = Files.list(directory())) { return files.filter(path -> path.toString().endsWith(".zip")).count(); } }
    private static void validate(String name, String reason) throws Exception {
        Path path = directory().resolve(name); require(Files.isRegularFile(path), "Archive missing");
        try (ZipFile zip = new ZipFile(path.toFile())) {
            require(zip.size() == 7, "Unexpected report entry count");
            StringBuilder all = new StringBuilder();
            var manifest = json(zip, "manifest.json").getAsJsonObject("files");
            for (var entry : Collections.list(zip.entries())) {
                byte[] bytes = zip.getInputStream(entry).readAllBytes(); all.append(new String(bytes, StandardCharsets.UTF_8));
                if (!entry.getName().equals("manifest.json")) {
                    var expected = manifest.getAsJsonObject(entry.getName());
                    require(expected.get("bytes").getAsLong() == bytes.length, "Manifest byte mismatch");
                    require(expected.get("sha256").getAsString().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))), "Manifest hash mismatch");
                }
            }
            for (String secret : List.of("ArcadiumTest", "Private fixture world", "arcadium-performance-report-qa", "C:\\Temp", "C:/Temp", "--accessToken", "private-profile-marker-8392")) require(!all.toString().contains(secret), "Privacy check failed: " + secret);
            JsonObject data = json(zip, "report.json"); var capture = data.getAsJsonObject("capture");
            require(reason.equals(capture.get("stopReason").getAsString()), "Wrong completion reason");
            require(capture.get("rawFrames").getAsInt() > 100, "No real frame data");
            String expectedVersion = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("arcadium_runtime").orElseThrow().getMetadata().getVersion().getFriendlyString();
            require(data.getAsJsonObject("environment").get("arcadiumVersion").getAsString().equals(expectedVersion), "Runtime build identity missing");
            var beta = data.getAsJsonObject("settingsAtStart").getAsJsonObject("numericAndBooleanModuleSettings").getAsJsonObject("arcadium-optimizations-beta.json");
            require(beta != null && beta.has("minimumPlayers") && beta.has("gpuPlayerPipeline") && beta.has("cachedNameMasking") && beta.has("cachedFontMetrics") && beta.has("batchedVanillaFont") && beta.size() == 22, "Not all Beta options were exported");
            require(data.has("optimizationStateAtStart") && data.has("optimizationStateLatest"), "Pipeline state snapshots missing");
            for (var timing : data.getAsJsonArray("allMainThreadTimings")) {
                var value = timing.getAsJsonObject(); String phase = value.get("phase").getAsString();
                if (!phase.equals("chunk_updates")) require(value.get("observed").getAsBoolean(), "Production timing hook not observed: " + phase);
            }
            require(json(zip, "profile.json").getAsJsonArray("stacks").size() > 0, "Real CPU profile missing");
            require(data.getAsJsonObject("gpu").getAsJsonArray("samples").size() > 0, "Asynchronous GPU data missing on this supported GPU");
            if (reason.equals("duration")) { require(capture.get("recordedSeconds").getAsDouble() >= 29, "Automatic capture ended early"); require(data.getAsJsonArray("settingsChanges").size() > 0, "Settings change not recorded"); }
            report.put(reason + "Report", data);
        }
    }

    private static JsonObject json(ZipFile zip, String name) throws Exception { return JsonParser.parseString(new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static void open() { OneConfigConfig.INSTANCE.getTree().getProp("guiMode").setAs(mode); OneConfigUI.open(ModsRoute.INSTANCE); }
    private static void next(int value) { phase = value; wait = 0; }
    private static Object helper(String name, Object... args) throws Exception {
        for (var method : OptimizationsUi066Qa.class.getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) { method.setAccessible(true); return method.invoke(null, args); }
        throw new NoSuchMethodException(name);
    }
    private static Object node(String value) throws Exception { return helper("textOrNull", value); }
    private static Object description(String value) throws Exception { return helper("descriptionOrNull", value); }
    private static void click(Object value) throws Exception { require(value != null, "UI action missing"); helper("click", value); }
    private static void screenshot(String name) { var mc = Minecraft.getInstance(); Screenshot.take(mc.gameDir, name + ".png", mc.width, mc.height, mc.getRenderTarget()); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void finish(Throwable failure) {
        done = true; if (controller != null && controller.snapshot().getBusy()) controller.cancel();
        report.put("passed", failure == null); report.put("phase", phase); report.put("archives", archives); report.put("syntheticOfflineFixture", true);
        if (failure != null) { report.put("error", failure.toString()); failure.printStackTrace(); try { screenshot("diagnostics-failure"); } catch (Throwable ignored) { } }
        try { Path output = Path.of(System.getProperty("arcadium.performanceCapture.output")); Files.createDirectories(output.toAbsolutePath().getParent()); Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
