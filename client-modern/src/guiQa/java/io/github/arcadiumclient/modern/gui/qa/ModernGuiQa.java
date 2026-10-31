// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.modern.client.ClientPreferences;
import io.github.arcadiumclient.modern.client.MenuPreferences;
import io.github.arcadiumclient.modern.client.background.PanoramaCatalog;
import io.github.arcadiumclient.modern.client.resourcepack.CustomPackMaker;
import io.github.arcadiumclient.modern.gui.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModernGuiQa implements ClientModInitializer {
    private record Step(String name, Runnable open, Class<?> expected) {}
    private final List<Step> steps = new ArrayList<>();
    private final List<Map<String, Object>> results = new ArrayList<>();
    private final AtomicBoolean capturePending = new AtomicBoolean();
    private Path output;
    private Minecraft minecraft;
    private Features062Qa features062;
    private int phase = -1;
    private int ticks;
    private int totalTicks;
    private boolean complete;
    private boolean runningNavigation;
    private Map<String, Object> navigationReport;
    private Map<String, Object> resourcePackPreview;
    private Map<String, Object> resourcePackActions;
    private boolean runningFonts;
    private boolean runningPlay;
    private Map<String, Object> playReport;
    private boolean startupCapturePending;
    private boolean startupCaptured;
    private boolean accessibilityContinued;
    private int initialBlur;
    private double initialPanoramaSpeed;
    private double noBlurContrast;
    private LocalStatusServer statusServer;
    private String makerName;
    private Map<String, Object> rendererControlProof;
    private Map<String, Object> nativeProfilesReport;
    private boolean nativeProfilesDone;
    private Map<String, Object> legacyNativeProfilesReport;
    private boolean legacyNativeProfilesDone;
    private WaypointsKeybindQa waypointsQa;
    private Attack059Qa attack059Qa;

    @Override public void onInitializeClient() {
        String destination = System.getProperty("arcadium.guiQa.output");
        if (destination == null || destination.isBlank()) throw new IllegalStateException("Set arcadium.guiQa.output for GUI QA");
        output = Path.of(destination).toAbsolutePath().normalize();
        QaRuntimeErrors.install();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (complete) return;
            minecraft = client;
            try {
                QaTickScheduler.drain();
                if (Boolean.getBoolean("arcadium.guiQa.attack059")) {
                    if (attack059Qa == null) attack059Qa = new Attack059Qa(output);
                    complete = attack059Qa.tick(client);
                } else if (Boolean.getBoolean("arcadium.guiQa.waypointsOnly")) {
                    if (waypointsQa == null) waypointsQa = new WaypointsKeybindQa(output);
                    complete = waypointsQa.tick(client);
                } else tick();
            }
            catch (Throwable failure) { fail(failure); }
        });
    }

    private void tick() throws Exception {
        if (Boolean.getBoolean("arcadium.guiQa.features062")) {
            if (features062 == null) features062 = new Features062Qa(output);
            complete = features062.tick(minecraft); return;
        }
        QaRuntimeErrors.check();
        if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("CrashScreen")) throw new IllegalStateException("Native crash screen opened: " + minecraft.screen.getClass().getName() + " / " + minecraft.screen.getTitle().getString());
        if (++totalTicks > (Boolean.getBoolean("arcadium.guiQa.play") || Boolean.getBoolean("arcadium.guiQa.playOnly") ? 8000 : 4000)) throw new IllegalStateException("GUI QA timed out");
        if (phase < 0) startupProgress();
        if (runningPlay || runningNavigation || runningFonts || minecraft.getOverlay() != null || capturePending.get()) return;
        if (phase < 0) {
            if (!(minecraft.screen instanceof ArcadiumMainMenu)) {
                if (totalTicks > 700) throw new IllegalStateException("Arcadium title screen was not reached; current screen: " + (minecraft.screen == null ? "null" : minecraft.screen.getClass().getName() + " / " + minecraft.screen.getTitle().getString()));
                return;
            }
            Files.createDirectories(output);
            ClientPreferences.setSetupComplete(true);
            if (Boolean.getBoolean("arcadium.guiQa.scoreboard060Only")) { Scoreboard060Proof.verify(minecraft, output); complete = true; minecraft.stop(); return; }
            initialBlur = MenuPreferences.getTitleBlurPercent();
            initialPanoramaSpeed = minecraft.options.panoramaSpeed().get();
            seedPacks();
            ResourcePackPreviewQa.prepare(minecraft);
            seedServers();
            steps.add(new Step("title", () -> minecraft.setScreen(new TitleScreen()), ArcadiumMainMenu.class));
            steps.add(new Step("title-blur-off", () -> { minecraft.options.panoramaSpeed().set(0.0); MenuPreferences.setTitleBlurPercent(0); minecraft.setScreen(new TitleScreen()); }, ArcadiumMainMenu.class));
            steps.add(new Step("title-blur-strong", () -> { MenuPreferences.setTitleBlurPercent(100); minecraft.setScreen(new TitleScreen()); }, ArcadiumMainMenu.class));
            steps.add(new Step("options", () -> { restoreBackgroundProbe(); OneConfigUI.open(); }, ComposeScreen.class));
            steps.add(new Step("worlds", () -> minecraft.setScreen(new SelectWorldScreen(new TitleScreen())), ArcadiumWorldsScreen.class));
            steps.add(new Step("servers", () -> minecraft.setScreen(new JoinMultiplayerScreen(new TitleScreen())), ArcadiumServersScreen.class));
            steps.add(new Step("server-preview", this::openDirectPreview, ArcadiumServersScreen.class));
            steps.add(new Step("video", () -> minecraft.setScreen(new VideoSettingsScreen(new TitleScreen(), minecraft, minecraft.options)), ArcadiumVideoSettingsScreen.class));
            steps.add(new Step("packs", () -> minecraft.setScreen(new PackSelectionScreen(minecraft.getResourcePackRepository(), ignored -> {}, minecraft.getResourcePackDirectory(), Component.literal("Resource Packs"))), ArcadiumResourcePacksScreen.class));
            steps.add(new Step("packs-all", () -> minecraft.setScreen(new ArcadiumResourcePacksScreen(new ArcadiumMainMenu())), ArcadiumResourcePacksScreen.class));
            steps.add(new Step("pack-maker", this::openMaker, CustomTexturePackScreen.class));
            steps.add(new Step("create-world", () -> CreateWorldScreen.openFresh(minecraft, () -> minecraft.setScreen(new ArcadiumMainMenu())), ArcadiumCreateWorldScreen.class));
            steps.add(new Step("background", () -> { MenuPreferences.setTitlePanorama("aurora-village"); minecraft.setScreen(new TitleBackgroundScreen(new ArcadiumMainMenu())); }, TitleBackgroundScreen.class));
            steps.add(new Step("onboarding", () -> { ClientPreferences.setSetupComplete(false); minecraft.setScreen(new TitleScreen()); }, ArcadiumMainMenu.class));
            steps.add(new Step("onboarding-sprint", () -> onboardPage(1), ArcadiumMainMenu.class));
            steps.add(new Step("onboarding-overlay", () -> onboardPage(2), ArcadiumMainMenu.class));
            steps.add(new Step("onboarding-finish", () -> onboardPage(3), ArcadiumMainMenu.class));
            steps.add(new Step("autotext", () -> minecraft.setScreen(new ArcadiumAutoTextScreen(new ArcadiumMainMenu())), ArcadiumAutoTextScreen.class));
            steps.add(new Step("crosshair", () -> minecraft.setScreen(new CustomCrosshairScreen(new ArcadiumMainMenu())), CustomCrosshairScreen.class));
            steps.add(new Step("skins", () -> minecraft.setScreen(new SkinChangerScreen(new ArcadiumMainMenu())), SkinChangerScreen.class));
            steps.add(new Step("accounts", () -> { ClientPreferences.setSetupComplete(true); minecraft.setScreen(new ArcadiumMainMenu(true)); }, ArcadiumMainMenu.class));
            if (Boolean.getBoolean("arcadium.guiQa.fontsOnly") || Boolean.getBoolean("arcadium.guiQa.nativeProfilesOnly")) {
                phase = steps.size();
                afterPlay();
                return;
            }
            if (Boolean.getBoolean("arcadium.guiQa.playOnly") || Boolean.getBoolean("arcadium.guiQa.packsOnly") || Boolean.getBoolean("arcadium.guiQa.skinsOnly")) {
                phase = steps.size();
                afterScreens();
                return;
            }
            if (Boolean.getBoolean("arcadium.guiQa.navigationOnly")) {
                phase = steps.size();
                afterScreens();
                return;
            }
            next();
            return;
        }
        if (++ticks < 45) return;
        Step step = steps.get(phase);
        if (!step.expected.isInstance(minecraft.screen) && ticks < 600) return;
        if (!step.expected.isInstance(minecraft.screen)) throw new IllegalStateException(step.name + " opened " + minecraft.screen);
        if (step.name.equals("server-preview")) {
            var field = minecraft.screen.getClass().getDeclaredField("directPreview$delegate"); field.setAccessible(true);
            ServerData preview = (ServerData) ((androidx.compose.runtime.State<?>) field.get(minecraft.screen)).getValue();
            if (preview == null || preview.ping < 0 || statusServer.requests() == 0) {
                if (ticks < 160) return;
                throw new IllegalStateException("Direct Connect did not receive the synthetic server status");
            }
            if (!preview.motd.getString().equals("Local server preview QA")) throw new IllegalStateException("Unexpected direct-connect MOTD");
        }
        if (step.name.equals("pack-maker")) {
            if (Boolean.TRUE.equals(getComposeState(minecraft.screen, "busy"))) return;
            if (getComposeState(minecraft.screen, "saved") == null) throw new IllegalStateException("Custom Texture Pack Maker did not finish: " + getComposeState(minecraft.screen, "error"));
            minecraft.getResourcePackRepository().reload();
            if (!minecraft.getResourcePackRepository().isAvailable("file/" + makerName + ".zip")) throw new IllegalStateException("Created custom pack was not discovered by the native repository");
        }
        if (step.name.equals("video") && FabricLoader.getInstance().isModLoaded("sodium")) {
            var field = minecraft.screen.getClass().getDeclaredField("model");
            field.setAccessible(true);
            Object model = field.get(minecraft.screen);
            if (!model.getClass().getSimpleName().equals("SodiumVideoSettingsModel")) throw new IllegalStateException("The renderer settings adapter fell back to vanilla");
            if (rendererControlProof == null) verifyRendererStorage((VideoSettingsModel) model);
        }
        if (step.name.equals("packs")) {
            if (resourcePackPreview == null) resourcePackPreview = ResourcePackPreviewQa.verify(minecraft.screen);
            String nested = "file/Bedwars/Clean/§bClean 16x";
            if (!minecraft.getResourcePackRepository().isAvailable(nested)) throw new IllegalStateException("Nested folder resource pack was not discovered");
            if (!minecraft.getResourcePackRepository().isAvailable("file/Bedwars/Extra.zip")) throw new IllegalStateException("Nested ZIP resource pack was not discovered");
            @SuppressWarnings("unchecked") List<?> views = (List<?>) getComposeState(minecraft.screen, "packs");
            var ids = new ArrayList<String>();
            int builtins = 0;
            for (Object view : views) {
                var id = view.getClass().getDeclaredField("id"); id.setAccessible(true); ids.add((String) id.get(view));
                var file = view.getClass().getDeclaredField("file"); file.setAccessible(true); if (file.get(view) == null) builtins++;
            }
            if (!ids.contains("Bedwars/Clean/§bClean 16x") || !ids.contains("Bedwars/Extra.zip")) throw new IllegalStateException("Discovered resource packs are absent from the UI entries: " + ids);
            if (minecraft.getResourcePackRepository().getAvailablePacks().stream().anyMatch(pack -> !pack.isRequired() && !pack.getId().startsWith("file/")) && builtins == 0)
                throw new IllegalStateException("Built-in resource packs are absent from the UI entries");
            setComposeState(minecraft.screen, "query", "clean");
        }
        if (step.name.equals("packs-all")) setComposeState(minecraft.screen, "expanded", java.util.Set.of("Bedwars", "Bedwars/Clean", "QA Previews", "QA Previews/Favorites"));
        if (step.name.equals("packs-all") && resourcePackActions == null) {
            capturePending.set(true);
            ResourcePackPreviewQa.run(minecraft, output).whenComplete((proof, failure) -> QaTickScheduler.execute(() -> {
                capturePending.set(false);
                if (failure != null) fail(failure);
                else { resourcePackActions = proof; ticks = 0; }
            }));
            return;
        }
        if (ticks < 65) return;
        capturePending.set(true);
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(step.name + ".png"));
                double contrast = backgroundContrast(image);
                if (step.name.equals("title-blur-off")) noBlurContrast = contrast;
                if (step.name.equals("title-blur-strong") && !(contrast < noBlurContrast * .96)) throw new IllegalStateException("Title background blur did not reduce image contrast: off=" + noBlurContrast + ", strong=" + contrast);
                var result = new LinkedHashMap<String, Object>();
                result.put("screen", step.name);
                result.put("replacement", minecraft.screen.getClass().getName());
                result.put("image", step.name + ".png");
                if (step.name.startsWith("title-blur")) result.put("backgroundContrast", contrast);
                synchronized (results) { results.add(result); }
                QaTickScheduler.execute(() -> {
                    try { next(); } catch (Throwable failure) { fail(failure); }
                    finally { capturePending.set(false); }
                });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> fail(failure)); }
        });
    }

    private void next() throws Exception {
        if (minecraft.screen instanceof ArcadiumCreateWorldScreen creation) ((Screen) creation).onClose();
        ticks = 0;
        if (++phase >= steps.size()) {
            ClientPreferences.setSetupComplete(true);
            afterScreens();
        } else steps.get(phase).open.run();
    }

    private void startupProgress() throws Exception {
        Files.createDirectories(output);
        if (totalTicks == 1 || totalTicks % 20 == 0 || minecraft.screen instanceof ArcadiumMainMenu) {
            var state = new LinkedHashMap<String, Object>();
            state.put("ticks", totalTicks);
            state.put("phase", phase);
            state.put("screen", minecraft.screen == null ? "null" : minecraft.screen.getClass().getName());
            state.put("title", minecraft.screen == null ? "" : minecraft.screen.getTitle().getString());
            state.put("overlay", minecraft.getOverlay() == null ? "null" : minecraft.getOverlay().getClass().getName());
            state.put("arcadiumTitle", minecraft.screen instanceof ArcadiumMainMenu);
            state.put("accessibilityOnboarding", minecraft.screen instanceof AccessibilityOnboardingScreen);
            state.put("accessibilityContinued", accessibilityContinued);
            Files.writeString(output.resolve("startup-progress.json"), new GsonBuilder().setPrettyPrinting().create().toJson(state));
        }
        if (startupCaptured || startupCapturePending || totalTicks < 20 || minecraft.getOverlay() != null || minecraft.screen == null) return;
        startupCapturePending = true;
        Screen captured = minecraft.screen;
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve("startup.png"));
                QaTickScheduler.execute(() -> {
                    startupCaptured = true;
                    startupCapturePending = false;
                    if (minecraft.screen == captured && captured instanceof AccessibilityOnboardingScreen onboarding) {
                        accessibilityContinued = true;
                        onboarding.onClose();
                    }
                });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> fail(failure)); }
        });
    }

    private void afterScreens() throws Exception {
        if ((Boolean.getBoolean("arcadium.guiQa.navigation") || Boolean.getBoolean("arcadium.guiQa.navigationOnly")) && navigationReport == null) {
            runningNavigation = true;
            ModernNavigationQa.run(minecraft, output).whenComplete((navigation, failure) -> QaTickScheduler.execute(() -> {
                runningNavigation = false;
                if (failure != null) fail(failure);
                else {
                    navigationReport = navigation;
                    try { afterScreens(); } catch (Throwable writing) { fail(writing); }
                }
            }));
            return;
        }
        if (Boolean.getBoolean("arcadium.guiQa.play") || Boolean.getBoolean("arcadium.guiQa.playOnly") || Boolean.getBoolean("arcadium.guiQa.packsOnly") || Boolean.getBoolean("arcadium.guiQa.skinsOnly")) {
            runningPlay = true;
            ModernPlayQa.run(minecraft, output).whenComplete((play, failure) -> QaTickScheduler.execute(() -> {
                if (failure != null) fail(failure);
                else {
                    playReport = play;
                    try { afterPlay(); } catch (Exception writing) { fail(writing); }
                }
            }));
        } else afterPlay();
    }

    private void afterPlay() throws Exception {
        if (!nativeProfilesDone && (Boolean.getBoolean("arcadium.guiQa.nativeProfiles") || Boolean.getBoolean("arcadium.guiQa.nativeProfilesOnly"))) {
            runningFonts = true;
            ModernNativeProfilesQa.run(output).whenComplete((profiles, failure) -> QaTickScheduler.execute(() -> {
                if (failure != null) fail(failure);
                else {
                    nativeProfilesReport = profiles;
                    nativeProfilesDone = true;
                    runningFonts = false;
                    try { afterPlay(); } catch (Exception writing) { fail(writing); }
                }
            }));
            return;
        }
        if (!legacyNativeProfilesDone && Boolean.getBoolean("arcadium.guiQa.legacyNativeProfiles")) {
            runningFonts = true;
            ModernLegacyNativeProfilesQa.run(output).whenComplete((profiles, failure) -> QaTickScheduler.execute(() -> {
                if (failure != null) fail(failure);
                else {
                    legacyNativeProfilesReport = profiles;
                    legacyNativeProfilesDone = true;
                    runningFonts = false;
                    try { afterPlay(); } catch (Exception writing) { fail(writing); }
                }
            }));
            return;
        }
        if (Boolean.getBoolean("arcadium.guiQa.fonts") || Boolean.getBoolean("arcadium.guiQa.fontsOnly")) {
            runningFonts = true;
            ModernFontQa.run(minecraft, output).whenComplete((fonts, failure) -> QaTickScheduler.execute(() -> {
                if (failure != null) fail(failure);
                else try { finish(fonts); } catch (Exception writing) { fail(writing); }
            }));
        } else finish(null);
    }

    private void finish(Map<String, Object> fonts) throws Exception {
        restoreBackgroundProbe();
        closeFixture();
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", playReport == null || Boolean.TRUE.equals(playReport.get("passed")));
        report.put("minecraft", SharedConstants.getCurrentVersion().name());
        report.put("panoramas", PanoramaCatalog.ENTRIES.size());
        report.put("screens", results);
        if (navigationReport != null) report.put("navigation", navigationReport);
        if (resourcePackPreview != null) report.put("resourcePackPreviews", resourcePackPreview);
        if (resourcePackActions != null) report.put("resourcePackActions", resourcePackActions);
        report.put("hardwareCoverage", hardwareCoverage());
        if (playReport != null) report.put("play", playReport);
        if (fonts != null) report.put("fonts", fonts);
        if (rendererControlProof != null) report.put("rendererControls", rendererControlProof);
        if (nativeProfilesReport != null) report.put("nativeProfiles", nativeProfilesReport);
        if (legacyNativeProfilesReport != null) report.put("legacyNativeProfiles", legacyNativeProfilesReport);
        if (Boolean.getBoolean("arcadium.guiQa.sharedOptions")) report.put("sharedOptions", SharedOptionsQa.verify(output));
        Files.writeString(output.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        complete = true;
        QaRuntimeErrors.stop();
        minecraft.stop();
    }

    private void seedServers() {
        var list = new ServerList(minecraft);
        list.add(new ServerData("Local QA A", "127.0.0.1:9", ServerData.Type.OTHER), false);
        list.add(new ServerData("Local QA B", "127.0.0.1:10", ServerData.Type.OTHER), false);
        list.save();
    }

    private void verifyRendererStorage(VideoSettingsModel model) throws Exception {
        var frameLimit = model.getOptions().stream().filter(option -> option.getKey().equals("max_framerate")).findFirst().orElseThrow();
        int previous = minecraft.options.framerateLimit().get();
        int next = previous == 200 ? 180 : 200;
        var data = new LinkedHashMap<String, Object>();
        rendererControlProof = data;
        data.put("model", model.getClass().getName());
        data.put("nativeBefore", previous);
        try {
            frameLimit.change(next);
            if (minecraft.options.framerateLimit().get() != previous) throw new IllegalStateException("Staged Sodium setting changed native options before Apply");
            model.apply();
            int applied = minecraft.options.framerateLimit().get();
            data.put("nativeApplied", applied);
            if (applied != next) throw new IllegalStateException("Sodium Apply did not update native max FPS");
            var optionsFile = minecraft.gameDirectory.toPath().resolve("options.txt");
            String stored = Files.readAllLines(optionsFile).stream().filter(line -> line.startsWith("maxFps:")).findFirst().orElseThrow();
            data.put("onDiskApplied", stored);
            if (!stored.equals("maxFps:" + next)) throw new IllegalStateException("Sodium Apply did not save native options.txt: " + stored);
            frameLimit.change(next - 20);
            model.undo();
            if (!frameLimit.getValue().equals(next) || minecraft.options.framerateLimit().get() != next) throw new IllegalStateException("Sodium Undo did not discard the staged change");
            data.put("undoPreservedNative", true);
        } finally {
            frameLimit.change(previous);
            model.apply();
            data.put("restored", minecraft.options.framerateLimit().get() == previous);
        }
        Files.writeString(output.resolve("renderer-controls.json"), new GsonBuilder().setPrettyPrinting().create().toJson(data));
    }

    private void openDirectPreview() {
        try {
            if (statusServer == null) statusServer = new LocalStatusServer();
            minecraft.setScreen(new ArcadiumServersScreen(new ArcadiumMainMenu()));
            setComposeState(minecraft.screen, "serverAddress", statusServer.address());
            var type = Class.forName("io.github.arcadiumclient.modern.gui.ServerDialog$Direct");
            var singleton = type.getDeclaredField("INSTANCE"); singleton.setAccessible(true);
            setComposeState(minecraft.screen, "dialog", singleton.get(null));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private void openMaker() {
        try {
            var directory = minecraft.getResourcePackDirectory();
            var source = new CustomPackMaker.Source("Clean 16x", directory.resolve("Bedwars/Clean/§bClean 16x"));
            var extra = new CustomPackMaker.Source("Extra", directory.resolve("Bedwars/Extra.zip"));
            minecraft.setScreen(new CustomTexturePackScreen(new ArcadiumMainMenu(), directory, List.of(source, extra), source, () -> kotlin.Unit.INSTANCE));
            makerName = "Arcadium Maker QA " + java.util.UUID.randomUUID().toString().substring(0, 8);
            setComposeState(minecraft.screen, "name", makerName);
            var create = minecraft.screen.getClass().getDeclaredMethod("create"); create.setAccessible(true); create.invoke(minecraft.screen);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private void seedPacks() throws Exception {
        Path directory = minecraft.getResourcePackDirectory();
        Path pack = directory.resolve("Bedwars/Clean/§bClean 16x");
        Files.createDirectories(pack);
        var version = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        var metadata = Map.of("pack", Map.of("description", "§aModern nested folder pack", "min_format", List.of(version.major(), version.minor()), "max_format", List.of(version.major(), version.minor())));
        String json = new GsonBuilder().create().toJson(metadata);
        Files.writeString(pack.resolve("pack.mcmeta"), json);
        Path zip = directory.resolve("Bedwars/Extra.zip");
        Files.createDirectories(zip.getParent());
        try (var archive = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            archive.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));
            archive.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        minecraft.getResourcePackRepository().reload();
    }

    @SuppressWarnings("unchecked")
    private static void setComposeState(Screen screen, String property, Object value) throws Exception {
        var field = screen.getClass().getDeclaredField(property + "$delegate");
        field.setAccessible(true);
        ((androidx.compose.runtime.MutableState<Object>) field.get(screen)).setValue(value);
    }

    private static Object getComposeState(Screen screen, String property) throws Exception {
        var field = screen.getClass().getDeclaredField(property + "$delegate");
        field.setAccessible(true);
        return ((androidx.compose.runtime.State<?>) field.get(screen)).getValue();
    }

    private void restoreBackgroundProbe() {
        MenuPreferences.setTitleBlurPercent(initialBlur);
        if (initialPanoramaSpeed >= 0) minecraft.options.panoramaSpeed().set(initialPanoramaSpeed);
    }

    private static double backgroundContrast(com.mojang.blaze3d.platform.NativeImage image) {
        long contrast = 0;
        int samples = 0;
        for (int y = image.getHeight() * 35 / 100; y < image.getHeight() * 70 / 100; y++) for (int x = image.getWidth() * 75 / 100; x < image.getWidth() * 94 / 100; x++) {
            int current = image.getPixel(x, y);
            int next = image.getPixel(x + 1, y);
            for (int shift = 0; shift < 24; shift += 8) contrast += Math.abs(((current >>> shift) & 255) - ((next >>> shift) & 255));
            samples += 3;
        }
        return samples == 0 ? 0 : (double) contrast / samples;
    }

    private void onboardPage(int page) {
        minecraft.setScreen(new TitleScreen());
        try { setComposeState(minecraft.screen, "page", page); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private void fail(Throwable failure) {
        if (complete) return;
        complete = true;
        failure.printStackTrace();
        if (phase >= 0) restoreBackgroundProbe();
        closeFixture();
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed", false, "phase", phase,
                    "error", failure.toString(), "screens", results, "runtimeErrors", QaRuntimeErrors.snapshot(),
                    "currentScreen", minecraft.screen == null ? "null" : minecraft.screen.getClass().getName(), "hardwareCoverage", hardwareCoverage())));
        } catch (Exception writing) { writing.printStackTrace(); }
        QaRuntimeErrors.stop();
        minecraft.stop();
    }

    private void closeFixture() {
        if (statusServer != null) try { statusServer.close(); } catch (Exception failure) { failure.printStackTrace(); }
    }

    private static Map<String, Object> hardwareCoverage() {
        return Map.of("voiceChatLoaded", FabricLoader.getInstance().isModLoaded("voicechat"),
                "localVoiceServerDisabledForQa", Boolean.getBoolean("arcadium.guiQa.microphoneIsolation"),
                "microphoneRecordingVerified", false,
                "microphoneLimitation", "This host has no usable microphone capture device. Local voice recording is not exercised.",
                "notificationAudioProof", "Native SoundManager.play calls and preference gating; no physical audio recording.");
    }
}
