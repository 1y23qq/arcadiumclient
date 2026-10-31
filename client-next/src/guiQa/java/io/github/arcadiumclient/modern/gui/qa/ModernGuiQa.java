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
    private int phase = -1;
    private int ticks;
    private int totalTicks;
    private boolean complete;
    private boolean runningNavigation;
    private Map<String, Object> navigationReport;
    private Map<String, Object> resourcePackPreview;
    private Map<String, Object> resourcePackActions;
    private boolean startupCapturePending;
    private boolean startupCaptured;
    private boolean accessibilityContinued;
    private int initialBlur;
    private double initialPanoramaSpeed;
    private double noBlurContrast;
    private LocalStatusServer statusServer;
    private String makerName;
    private Map<String, Object> rendererControlProof;
    private Map<String, Object> dragProof;
    private PackPointerQa packPointer;
    private boolean runningWorld;
    private Map<String, Object> playReport;
    private Map<String, Object> potionPropertyProof;
    private org.polyfrost.oneconfig.api.config.v1.Property<?> potionScopes;
    private Object previousPotionScopes;

    @Override public void onInitializeClient() {
        String destination = System.getProperty("arcadium.guiQa.output");
        if (destination == null || destination.isBlank()) throw new IllegalStateException("Set arcadium.guiQa.output for GUI QA");
        output = Path.of(destination).toAbsolutePath().normalize();
        QaRuntimeErrors.install();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (complete) return;
            minecraft = client;
            try { QaTickScheduler.drain(); tick(); }
            catch (Throwable failure) { fail(failure); }
        });
    }

    private void tick() throws Exception {
        QaRuntimeErrors.check();
        if (minecraft.gui.screen() != null && minecraft.gui.screen().getClass().getSimpleName().equals("CrashScreen")) throw new IllegalStateException("Native crash screen opened: " + minecraft.gui.screen().getClass().getName() + " / " + minecraft.gui.screen().getTitle().getString());
        if (++totalTicks > 4000) throw new IllegalStateException("GUI QA timed out");
        if (phase < 0) startupProgress();
        if (runningWorld || runningNavigation || minecraft.gui.overlay() != null || capturePending.get()) return;
        if (phase < 0) {
            if (!(minecraft.gui.screen() instanceof ArcadiumMainMenu)) {
                if (totalTicks > 700) throw new IllegalStateException("Arcadium title screen was not reached; current screen: " + (minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getName() + " / " + minecraft.gui.screen().getTitle().getString()));
                return;
            }
            Files.createDirectories(output);
            ClientPreferences.setSetupComplete(true);
            if (Boolean.getBoolean("arcadium.guiQa.scoreboard060Only")) { Scoreboard060Proof.verify(minecraft, output); complete = true; minecraft.stop(); return; }
            initialBlur = MenuPreferences.getTitleBlurPercent();
            initialPanoramaSpeed = minecraft.options.panoramaSpeed().get();
            if (!worldOnly()) { seedPacks(); ResourcePackPreviewQa.prepare(minecraft); seedServers(); }
            steps.add(new Step("title", () -> minecraft.gui.setScreen(new TitleScreen()), ArcadiumMainMenu.class));
            steps.add(new Step("title-blur-off", () -> { minecraft.options.panoramaSpeed().set(0.0); MenuPreferences.setTitleBlurPercent(0); minecraft.gui.setScreen(new TitleScreen()); }, ArcadiumMainMenu.class));
            steps.add(new Step("title-blur-strong", () -> { MenuPreferences.setTitleBlurPercent(100); minecraft.gui.setScreen(new TitleScreen()); }, ArcadiumMainMenu.class));
            steps.add(new Step("options", () -> { restoreBackgroundProbe(); OneConfigUI.open(); }, ComposeScreen.class));
            steps.add(new Step("chat-shortcuts", () -> {
                try { minecraft.gui.setScreen((Screen) Class.forName("org.polyfrost.chatting.config.shortcut.ChatShortcutManagerScreen").getConstructor().newInstance()); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }, ComposeScreen.class));
            steps.add(new Step("potion-override-options", () -> {
                var provider = org.polyfrost.oneconfig.api.hud.v1.HudManager.INSTANCE.providers().stream()
                        .filter(hud -> hud.getClass().getName().equals("org.polyfrost.evergreenhud.client.hud.potion.PotionEffectsHud")).findFirst().orElseThrow();
                var existing = org.polyfrost.oneconfig.api.hud.v1.HudManager.INSTANCE.getActiveInstances().stream()
                        .filter(hud -> hud.getClass() == provider.getClass()).findFirst().orElse(null);
                var hud = existing == null ? provider.make(null) : existing;
                if (existing == null) hud.setup();
                var scopes = hud.getTree().getProp("overrides");
                if (scopes == null) throw new IllegalStateException("Native Potion Effects scope list is absent");
                potionScopes = scopes;
                Object oldScopes = scopes.get();
                previousPotionScopes = oldScopes instanceof String[] values ? values.clone() : oldScopes;
                scopes.setAs(new String[]{"Beneficial Effects"});
                var property = hud.getTree().getProp("beneficial", "overriddenOptions");
                if (property == null) throw new IllegalStateException("Native Potion Effects overrides property is absent");
                Object visualizer = property.getMetadata("visualizer");
                potionPropertyProof = Map.of("hudClass", hud.getClass().getName(), "tree", hud.getTree().getID(),
                        "visualizer", visualizer == null ? "null" : visualizer instanceof Class<?> type ? type.getName() : visualizer.getClass().getName(),
                        "nativeSetup", true, "property", "beneficial.overriddenOptions");
                minecraft.gui.setScreen(new PotionOverridesQaScreen(property));
            }, ComposeScreen.class));
            steps.add(new Step("worlds", () -> minecraft.gui.setScreen(new SelectWorldScreen(new TitleScreen())), ArcadiumWorldsScreen.class));
            steps.add(new Step("servers", () -> minecraft.gui.setScreen(new JoinMultiplayerScreen(new TitleScreen())), ArcadiumServersScreen.class));
            steps.add(new Step("server-preview", this::openDirectPreview, ArcadiumServersScreen.class));
            steps.add(new Step("video", () -> minecraft.gui.setScreen(new VideoSettingsScreen(new TitleScreen(), minecraft, minecraft.options)), ArcadiumVideoSettingsScreen.class));
            steps.add(new Step("packs", () -> minecraft.gui.setScreen(new PackSelectionScreen(minecraft.getResourcePackRepository(), ignored -> {}, minecraft.getResourcePackDirectory(), Component.literal("Resource Packs"))), ArcadiumResourcePacksScreen.class));
            steps.add(new Step("packs-all", () -> minecraft.gui.setScreen(new ArcadiumResourcePacksScreen(new ArcadiumMainMenu())), ArcadiumResourcePacksScreen.class));
            steps.add(new Step("packs-drag", () -> minecraft.gui.setScreen(new ArcadiumResourcePacksScreen(new ArcadiumMainMenu())), ArcadiumResourcePacksScreen.class));
            steps.add(new Step("pack-maker", this::openMaker, CustomTexturePackScreen.class));
            steps.add(new Step("create-world", () -> CreateWorldScreen.openFresh(minecraft, () -> minecraft.gui.setScreen(new ArcadiumMainMenu())), ArcadiumCreateWorldScreen.class));
            steps.add(new Step("background", () -> { MenuPreferences.setTitlePanorama("aurora-village"); minecraft.gui.setScreen(new TitleBackgroundScreen(new ArcadiumMainMenu())); }, TitleBackgroundScreen.class));
            steps.add(new Step("onboarding", () -> { ClientPreferences.setSetupComplete(false); minecraft.gui.setScreen(new TitleScreen()); }, ArcadiumMainMenu.class));
            steps.add(new Step("onboarding-sprint", () -> onboardPage(1), ArcadiumMainMenu.class));
            steps.add(new Step("onboarding-overlay", () -> onboardPage(2), ArcadiumMainMenu.class));
            steps.add(new Step("onboarding-finish", () -> onboardPage(3), ArcadiumMainMenu.class));
            steps.add(new Step("autotext", () -> minecraft.gui.setScreen(new ArcadiumAutoTextScreen(new ArcadiumMainMenu())), ArcadiumAutoTextScreen.class));
            steps.add(new Step("crosshair", () -> minecraft.gui.setScreen(new CustomCrosshairScreen(new ArcadiumMainMenu())), CustomCrosshairScreen.class));
            steps.add(new Step("skins", () -> minecraft.gui.setScreen(new SkinChangerScreen(new ArcadiumMainMenu())), SkinChangerScreen.class));
            steps.add(new Step("accounts", () -> { ClientPreferences.setSetupComplete(true); minecraft.gui.setScreen(new ArcadiumMainMenu(true)); }, ArcadiumMainMenu.class));
            if (worldOnly()) {
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
        if (!step.expected.isInstance(minecraft.gui.screen()) && ticks < 600) return;
        if (!step.expected.isInstance(minecraft.gui.screen())) throw new IllegalStateException(step.name + " opened " + minecraft.gui.screen());
        if (step.name.equals("packs-drag")) {
            if (packPointer == null) packPointer = new PackPointerQa(minecraft);
            if (!packPointer.tick()) return;
            dragProof = packPointer.proof();
        }
        if (step.name.equals("potion-override-options") && PotionVisualizerQa.draws() == 0) {
            if (ticks > 220) throw new IllegalStateException("Native Potion Effects override visualizer was never composed");
            if (ticks % 20 == 0) {
                var window = minecraft.getWindow();
                ((io.github.arcadiumclient.modern.gui.qa.mixin.MouseQaAccess) minecraft.mouseHandler)
                        .arcadium$nativeMove(window.handle(), window.getWidth() * .86, window.getHeight() * .65);
                minecraft.gui.screen().mouseScrolled(minecraft.gui.screen().width * .86, minecraft.gui.screen().height * .65, 0, -12);
            }
            return;
        }
        if (step.name.equals("server-preview")) {
            var field = minecraft.gui.screen().getClass().getDeclaredField("directPreview$delegate"); field.setAccessible(true);
            ServerData preview = (ServerData) ((androidx.compose.runtime.State<?>) field.get(minecraft.gui.screen())).getValue();
            if (preview == null || preview.ping < 0 || statusServer.requests() == 0) {
                if (ticks < 160) return;
                throw new IllegalStateException("Direct Connect did not receive the synthetic server status");
            }
            if (!preview.motd.getString().equals("Local server preview QA")) throw new IllegalStateException("Unexpected direct-connect MOTD");
        }
        if (step.name.equals("pack-maker")) {
            if (Boolean.TRUE.equals(getComposeState(minecraft.gui.screen(), "busy"))) return;
            if (getComposeState(minecraft.gui.screen(), "saved") == null) throw new IllegalStateException("Custom Texture Pack Maker did not finish: " + getComposeState(minecraft.gui.screen(), "error"));
            minecraft.getResourcePackRepository().reload();
            if (!minecraft.getResourcePackRepository().isAvailable("file/" + makerName + ".zip")) throw new IllegalStateException("Created custom pack was not discovered by the native repository");
        }
        if (step.name.equals("video") && FabricLoader.getInstance().isModLoaded("sodium")) {
            var field = minecraft.gui.screen().getClass().getDeclaredField("model");
            field.setAccessible(true);
            Object model = field.get(minecraft.gui.screen());
            if (!model.getClass().getSimpleName().equals("SodiumVideoSettingsModel")) throw new IllegalStateException("The renderer settings adapter fell back to vanilla");
            if (rendererControlProof == null) verifyRendererStorage((VideoSettingsModel) model);
        }
        if (step.name.equals("packs")) {
            if (resourcePackPreview == null) resourcePackPreview = ResourcePackPreviewQa.verify(minecraft.gui.screen());
            String nested = "file/Bedwars/Clean/§bClean 16x";
            if (!minecraft.getResourcePackRepository().isAvailable(nested)) throw new IllegalStateException("Nested folder resource pack was not discovered");
            if (!minecraft.getResourcePackRepository().isAvailable("file/Bedwars/Extra.zip")) throw new IllegalStateException("Nested ZIP resource pack was not discovered");
            @SuppressWarnings("unchecked") List<?> views = (List<?>) getComposeState(minecraft.gui.screen(), "packs");
            var ids = new ArrayList<String>();
            int builtins = 0;
            for (Object view : views) {
                var id = view.getClass().getDeclaredField("id"); id.setAccessible(true); ids.add((String) id.get(view));
                var file = view.getClass().getDeclaredField("file"); file.setAccessible(true); if (file.get(view) == null) builtins++;
            }
            if (!ids.contains("Bedwars/Clean/§bClean 16x") || !ids.contains("Bedwars/Extra.zip")) throw new IllegalStateException("Discovered resource packs are absent from the UI entries: " + ids);
            if (minecraft.getResourcePackRepository().getAvailablePacks().stream().anyMatch(pack -> !pack.isRequired() && !pack.getId().startsWith("file/")) && builtins == 0)
                throw new IllegalStateException("Built-in resource packs are absent from the UI entries");
            setComposeState(minecraft.gui.screen(), "query", "clean");
        }
        if (step.name.equals("packs-all")) setComposeState(minecraft.gui.screen(), "expanded", java.util.Set.of("Bedwars", "Bedwars/Clean", "QA Previews", "QA Previews/Favorites"));
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
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(step.name + ".png"));
                double contrast = backgroundContrast(image);
                if (step.name.equals("title-blur-off")) noBlurContrast = contrast;
                if (step.name.equals("title-blur-strong") && !(contrast < noBlurContrast * .96)) throw new IllegalStateException("Title background blur did not reduce image contrast: off=" + noBlurContrast + ", strong=" + contrast);
                var result = new LinkedHashMap<String, Object>();
                result.put("screen", step.name);
                result.put("replacement", minecraft.gui.screen().getClass().getName());
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
        restorePotionScopes();
        if (minecraft.gui.screen() instanceof ArcadiumCreateWorldScreen creation) ((Screen) creation).onClose();
        ticks = 0;
        if (++phase >= steps.size()) {
            ClientPreferences.setSetupComplete(true);
            afterScreens();
        } else steps.get(phase).open.run();
    }

    private void startupProgress() throws Exception {
        Files.createDirectories(output);
        if (totalTicks == 1 || totalTicks % 20 == 0 || minecraft.gui.screen() instanceof ArcadiumMainMenu) {
            var state = new LinkedHashMap<String, Object>();
            state.put("ticks", totalTicks);
            state.put("phase", phase);
            state.put("screen", minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getName());
            state.put("title", minecraft.gui.screen() == null ? "" : minecraft.gui.screen().getTitle().getString());
            state.put("overlay", minecraft.gui.overlay() == null ? "null" : minecraft.gui.overlay().getClass().getName());
            state.put("arcadiumTitle", minecraft.gui.screen() instanceof ArcadiumMainMenu);
            state.put("accessibilityOnboarding", minecraft.gui.screen() instanceof AccessibilityOnboardingScreen);
            state.put("accessibilityContinued", accessibilityContinued);
            Files.writeString(output.resolve("startup-progress.json"), new GsonBuilder().setPrettyPrinting().create().toJson(state));
        }
        if (startupCaptured || startupCapturePending || totalTicks < 20 || minecraft.gui.overlay() != null || minecraft.gui.screen() == null) return;
        startupCapturePending = true;
        Screen captured = minecraft.gui.screen();
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve("startup.png"));
                QaTickScheduler.execute(() -> {
                    startupCaptured = true;
                    startupCapturePending = false;
                    if (minecraft.gui.screen() == captured && captured instanceof AccessibilityOnboardingScreen onboarding) {
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
        if ((Boolean.getBoolean("arcadium.guiQa.play") || worldOnly()) && playReport == null) {
            runningWorld = true;
            ModernWorldQa.run(minecraft, output).whenComplete((play, failure) -> QaTickScheduler.execute(() -> {
                if (failure != null) fail(failure);
                else {
                    playReport = play; runningWorld = false;
                    try { afterScreens(); } catch (Throwable writing) { fail(writing); }
                }
            }));
            return;
        }
        restoreBackgroundProbe();
        closeFixture();
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", true);
        report.put("minecraft", SharedConstants.getCurrentVersion().name());
        report.put("panoramas", PanoramaCatalog.ENTRIES.size());
        report.put("screens", results);
        if (navigationReport != null) report.put("navigation", navigationReport);
        if (resourcePackPreview != null) report.put("resourcePackPreviews", resourcePackPreview);
        if (resourcePackActions != null) report.put("resourcePackActions", resourcePackActions);
        report.put("qaScope", Boolean.getBoolean("arcadium.guiQa.navigationOnly") ? "navigation-only" : Boolean.getBoolean("arcadium.guiQa.runtimeOnly") ? "runtime-only" : worldOnly() ? "world-only" : "menus-and-requested-world");
        report.put("runtimeErrors", QaRuntimeErrors.snapshot());
        report.put("rendererControls", rendererControlProof);
        report.put("resourcePackDrag", dragProof);
        report.put("nativePotionVisualizerDraws", PotionVisualizerQa.draws());
        report.put("potionProperty", potionPropertyProof);
        if (playReport != null) report.put("play", playReport);
        if (playReport == null) report.putAll(NativeGraphicsQa.snapshot());
        else for (String key : List.of("graphicsBackend", "gpuName", "gpuVendor", "gpuDriver", "gpuDevice", "nvidiumLoaded", "nvidiumCompatible", "nvidiumEnabled"))
            if (playReport.containsKey(key)) report.put(key, playReport.get(key));
        report.put("captureTarget", "Minecraft native main render target after GUI extraction and composition");
        if (!worldOnly() && !Boolean.getBoolean("arcadium.guiQa.navigationOnly")) {
            if (results.size() != steps.size()) throw new IllegalStateException("Not every GUI screen was captured");
            if (dragProof == null || !Boolean.TRUE.equals(dragProof.get("applied"))) throw new IllegalStateException("Resource pack drag proof is missing");
        }
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
        var renderer = model.getOptions().stream().filter(option -> option.getKey().equals("graphics_backend")).findFirst().orElseThrow();
        var previousBackend = minecraft.options.preferredGraphicsBackend().get();
        var stagedBackend = previousBackend == net.minecraft.client.PreferredGraphicsApi.OPENGL
                ? net.minecraft.client.PreferredGraphicsApi.VULKAN : net.minecraft.client.PreferredGraphicsApi.OPENGL;
        renderer.change(stagedBackend);
        if (minecraft.options.preferredGraphicsBackend().get() != previousBackend || !model.getRestartPending())
            throw new IllegalStateException("Graphics API selection was not staged with a restart requirement");
        model.undo();
        if (renderer.getValue() != previousBackend || minecraft.options.preferredGraphicsBackend().get() != previousBackend)
            throw new IllegalStateException("Graphics API Undo did not restore the current renderer");
        data.put("graphicsApiStagedAndUndo", true);
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
            minecraft.gui.setScreen(new ArcadiumServersScreen(new ArcadiumMainMenu()));
            setComposeState(minecraft.gui.screen(), "serverAddress", statusServer.address());
            var type = Class.forName("io.github.arcadiumclient.modern.gui.ServerDialog$Direct");
            var singleton = type.getDeclaredField("INSTANCE"); singleton.setAccessible(true);
            setComposeState(minecraft.gui.screen(), "dialog", singleton.get(null));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private void openMaker() {
        try {
            var directory = minecraft.getResourcePackDirectory();
            var source = new CustomPackMaker.Source("Clean 16x", directory.resolve("Bedwars/Clean/§bClean 16x"));
            var extra = new CustomPackMaker.Source("Extra", directory.resolve("Bedwars/Extra.zip"));
            minecraft.gui.setScreen(new CustomTexturePackScreen(new ArcadiumMainMenu(), directory, List.of(source, extra), source, () -> kotlin.Unit.INSTANCE));
            makerName = "Arcadium Maker QA " + java.util.UUID.randomUUID().toString().substring(0, 8);
            setComposeState(minecraft.gui.screen(), "name", makerName);
            var create = minecraft.gui.screen().getClass().getDeclaredMethod("create"); create.setAccessible(true); create.invoke(minecraft.gui.screen());
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
        minecraft.gui.setScreen(new TitleScreen());
        try { setComposeState(minecraft.gui.screen(), "page", page); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private void fail(Throwable failure) {
        if (complete) return;
        complete = true;
        failure.printStackTrace();
        restorePotionScopes();
        if (phase >= 0) restoreBackgroundProbe();
        closeFixture();
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed", false, "phase", phase,
                    "error", failure.toString(), "screens", results, "runtimeErrors", QaRuntimeErrors.snapshot(),
                    "currentScreen", minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getName(),
                    "gpuDevice", com.mojang.blaze3d.systems.RenderSystem.getDevice().getClass().getName())));
        } catch (Exception writing) { writing.printStackTrace(); }
        QaRuntimeErrors.stop();
        minecraft.stop();
    }

    private void closeFixture() {
        if (statusServer != null) try { statusServer.close(); } catch (Exception failure) { failure.printStackTrace(); }
    }
    private static boolean worldOnly() { return Boolean.getBoolean("arcadium.guiQa.playOnly") || Boolean.getBoolean("arcadium.guiQa.runtimeOnly"); }

    private void restorePotionScopes() {
        if (potionScopes == null) return;
        potionScopes.setAs(previousPotionScopes);
        potionScopes = null; previousPotionScopes = null;
    }

}
