// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.arcadiumclient.modern.client.features.*;
import io.github.arcadiumclient.modern.gui.ArcadiumCreateWorldScreen;
import io.github.arcadiumclient.modern.gui.ArcadiumMainMenu;
import io.github.arcadiumclient.modern.gui.ArcadiumPauseScreen;
import io.github.arcadiumclient.modern.gui.qa.mixin.ChatQaAccess;
import io.github.arcadiumclient.modern.gui.qa.mixin.KeyboardQaAccess;
import io.github.arcadiumclient.modern.mixin.gui.CreateWorldAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.polyfrost.compose.node.McTextNode;
import org.polyfrost.compose.node.PolyNode;
import org.polyfrost.compose.node.TextNode;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.hud.v1.Font;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.Section;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager;
import org.lwjgl.glfw.GLFW;

final class ModernWorldQa {
    private static final String HEALTH = "io.github.arcadiumclient.runtime.client.features.HealthHud";
    private static final String HEIGHT = "io.github.arcadiumclient.runtime.client.features.HeightLimitHud";
    private static final String COUNTER = "org.polyfrost.evergreenhud.client.hud.ItemCounterHud";
    private final Minecraft minecraft;
    private final Path output;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> captures = new ArrayList<>();
    private final Map<KeyMapping, String> keys = new LinkedHashMap<>();
    private final String marker = UUID.randomUUID().toString().substring(0, 8);
    private final String worldName = "Arcadium 26.2 QA " + marker;
    private final String previousProfile = ConfigManager.activeProfile();
    private final String profile = "Arcadium 26.2 QA " + marker;
    private final CrosshairDesign previousCrosshair = CustomCrosshair.design().copy();
    private final boolean previousEnabled = CustomCrosshair.INSTANCE.enabled();
    private final boolean previousThirdPerson = CustomCrosshairConfig.getThirdPerson();
    private final boolean previousAutoText = AutoTextConfig.getEnabled();
    private final CameraType previousCamera;
    private final int previousDistance;
    private HealthHud health;
    private HeightLimitHud height;
    private Hud counter;
    private String autoId;
    private String worldId;
    private String healthId, heightId, counterId;
    private int phase, ticks, totalTicks, sentTick, canvasPixels;
    private boolean waiting, cleaning, complete;
    private boolean fontsDone;
    private Throwable failure;
    private IntegratedServer closingServer;
    private long closingAt;
    private net.minecraft.client.gui.components.ChatComponent.State chatState;
    private float horizontalWidth, horizontalHeight;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        try {
            if (minecraft.level != null || minecraft.getSingleplayerServer() != null) throw new IllegalStateException("World QA must start outside a world");
            var qa = new ModernWorldQa(minecraft, output);
            qa.start();
            return qa.completion;
        } catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    }

    private ModernWorldQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft; this.output = output;
        previousCamera = minecraft.options.getCameraType();
        previousDistance = minecraft.options.renderDistance().get();
    }

    private void start() {
        minecraft.options.renderDistance().set(4);
        CreateWorldScreen.openFresh(minecraft, () -> minecraft.gui.setScreen(new ArcadiumMainMenu()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (complete) return;
            try {
                if (cleaning) { pollCleanup(); return; }
                if (++totalTicks > 6000) throw new IllegalStateException("World QA timed out in phase " + phase);
                if (!waiting && minecraft.gui.overlay() == null) tick();
            } catch (Throwable cause) { cleanup(cause); }
        });
    }

    private void tick() throws Exception {
        QaRuntimeErrors.check();
        ticks++;
        switch (phase) {
            case 0 -> {
                if (!(minecraft.gui.screen() instanceof ArcadiumCreateWorldScreen screen)) return;
                var field = screen.getClass().getDeclaredField("delegate"); field.setAccessible(true);
                var nativeScreen = (CreateWorldScreen) field.get(screen);
                var state = nativeScreen.getUiState();
                state.setName(worldName); state.setGameMode(SelectedGameMode.CREATIVE); state.setAllowCommands(true);
                state.setGenerateStructures(false); state.setBonusChest(false); state.setSeed("16438450");
                state.setWorldType(state.getNormalPresetList().stream().filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow());
                worldId = state.getTargetFolder();
                ((CreateWorldAccess) nativeScreen).arcadium$create();
                next();
            }
            case 1 -> {
                if (minecraft.level == null || minecraft.player == null || !minecraft.hasSingleplayerServer() || ticks < 60) return;
                require(minecraft.getSingleplayerServer().getWorldData().getLevelName().equals(worldName), "Synthetic world identity differs");
                minecraft.gui.setScreen(null);
                if (Boolean.getBoolean("arcadium.guiQa.screenEscapeOnly")) {
                    waiting = true;
                    report.put("qaScope", "screen-escape-only");
                    ModernScreenEscapeQa.run(minecraft, output).whenComplete((screens, failure) -> QaTickScheduler.execute(() -> {
                        waiting = false;
                        if (failure == null) report.put("screenEscape", screens);
                        cleanup(failure);
                    }));
                    return;
                }
                if (Boolean.getBoolean("arcadium.guiQa.runtimeOnly")) {
                    seedPlayer(); phase = 12; ticks = 0;
                    report.put("qaScope", "runtime-only");
                    return;
                }
                setupHuds(); seedPlayer(); setupCanvas(); next();
            }
            case 2 -> {
                if (ticks < 30) return;
                health = (HealthHud) HudManager.INSTANCE.instanceById(healthId);
                height = (HeightLimitHud) HudManager.INSTANCE.instanceById(heightId);
                counter = HudManager.INSTANCE.instanceById(counterId);
                require(health != null && height != null && counter != null, "Current live HUDs disappeared after profile reload");
                health.update(); height.update();
                report.put("nativeHealth", minecraft.player.getHealth());
                report.put("liveHudDiagnostics", List.of(health, height, counter).stream().map(ModernWorldQa::hudState).toList());
                if (!text(health).contains("8.5")) {
                    if (ticks < 60) return;
                    failCapture("world-health-missing", new IllegalStateException("Health HUD did not read17HP as8.5hearts: " + text(health)));
                    return;
                }
                int current = (int) Math.floor(minecraft.player.getY()), limit = minecraft.level.getMaxY() + 1;
                String expected = current + "/" + limit + " (" + Math.max(0, limit - current) + ")";
                require(text(height).equals(expected), "Height HUD did not read native world bounds: " + text(height));
                var rows = ItemCounterRows.recompute(counter, new String[]{"minecraft:stone", "minecraft:ender_pearl"});
                require(rows.size() == 2 && rows.stream().anyMatch(row -> row.getText().equals("32"))
                        && rows.stream().anyMatch(row -> row.getText().equals("5")), "Item Counter did not read native inventory");
                horizontalWidth = counter.getRenderedW(); horizontalHeight = counter.getRenderedH();
                report.put("healthText", text(health)); report.put("heightText", text(height));
                report.put("itemCounters", rows.stream().map(row -> Map.of("text", row.getText())).toList());
                capture("world-first-person", true);
            }
            case 3, 4 -> {
                if (ticks == 1) minecraft.options.setCameraType(phase == 3 ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
                if (ticks >= 20) capture(phase == 3 ? "world-third-person-back" : "world-third-person-front", true);
            }
            case 5 -> {
                if (ticks == 1) {
                    minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                    counter.getTree().getProp("arcadium$itemCounterLayout").setAs(0); counter.update();
                }
                if (ticks < 20) return;
                require(counter.getRenderedH() > horizontalHeight && counter.getRenderedW() < horizontalWidth, "Item Counter vertical layout geometry did not change");
                report.put("counterLayout", Map.of("horizontal", List.of(horizontalWidth, horizontalHeight), "vertical", List.of(counter.getRenderedW(), counter.getRenderedH())));
                capture("world-counter-vertical", false);
            }
            case 6 -> {
                setupAutoText(); minecraft.gui.setScreen(new ChatScreen("/say CHAT_" + marker, false));
                key(GLFW.GLFW_KEY_V, GLFW.GLFW_PRESS); next();
            }
            case 7 -> {
                if (ticks == 1) key(GLFW.GLFW_KEY_V, GLFW.GLFW_RELEASE);
                if (ticks < 10) return;
                require(!chatContains("AUTO_" + marker), "AutoText fired while typing");
                sentTick = totalTicks; key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_PRESS); next();
            }
            case 8 -> {
                if (ticks == 1) key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
                if (!chatContains("CHAT_" + marker)) { require(ticks < 120, "Native Enter did not reach the server"); return; }
                require(minecraft.gui.screen() == null, "Native Enter did not close chat");
                report.put("nativeEnterDeliveryTicks", totalTicks - sentTick);
                sentTick = totalTicks; key(GLFW.GLFW_KEY_V, GLFW.GLFW_PRESS); next();
            }
            case 9 -> {
                if (ticks == 1) key(GLFW.GLFW_KEY_V, GLFW.GLFW_RELEASE);
                if (!chatContains("AUTO_" + marker)) { require(ticks < 120, "Native AutoText V did not reach the server"); return; }
                report.put("nativeAutoTextDeliveryTicks", totalTicks - sentTick);
                minecraft.gui.setScreen(new PauseScreen(true));
                require(minecraft.gui.screen() instanceof ArcadiumPauseScreen, "Native pause menu was not replaced");
                next();
            }
            case 10 -> { if (ticks >= 20) capture("world-pause", false); }
            case 11 -> {
                if (ticks == 1) {
                    minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("ARCADIUM_OPTIONS_CHAT_" + marker)); OneConfigUI.open();
                }
                if (ticks >= 20) {
                    require(chatContains("ARCADIUM_OPTIONS_CHAT_" + marker), "Options chat fixture is absent");
                    capture("world-options-chat", false);
                }
            }
            case 12 -> {
                if (Boolean.getBoolean("arcadium.guiQa.runtimeOnly") && ticks < 20) return;
                report.putAll(NativeGraphicsQa.snapshot());
                if (!Boolean.getBoolean("arcadium.guiQa.runtimeOnly") && Boolean.getBoolean("arcadium.guiQa.fonts") && !fontsDone) {
                    waiting = true;
                    NativeFont262Qa.run(minecraft, output).whenComplete((fonts, cause) -> QaTickScheduler.execute(() -> {
                        if (cause != null) cleanup(cause);
                        else { report.put("fonts", fonts); fontsDone = true; waiting = false; }
                    }));
                    return;
                }
                if (!Boolean.getBoolean("arcadium.guiQa.runtime") && !Boolean.getBoolean("arcadium.guiQa.runtimeOnly")) { cleanup(null); return; }
                waiting = true;
                ModernRuntimeQa.run(minecraft, output).whenComplete((runtime, cause) -> QaTickScheduler.execute(() -> {
                    if (cause != null) cleanup(cause);
                    else { report.put("runtime", runtime); cleanup(null); }
                }));
            }
        }
    }

    private void setupHuds() {
        var providers = HudManager.INSTANCE.providers();
        report.put("hudProviders", providers.stream().map(hud -> hud.getClass().getName()).sorted().toList());
        ConfigManager.createProfile(profile); ConfigManager.openProfile(profile);
        health = (HealthHud) provider(HEALTH).make(null); height = (HeightLimitHud) provider(HEIGHT).make(null); counter = provider(COUNTER).make(null);
        style(health, 40, 55); style(height, 40, 85); style(counter, 40, 115);
        health.setUnits(0); height.setStyle(2); height.setFormat("#current/#limit (#distance)"); height.setSpaceBeforeDistance(true);
        counter.getTree().getProp("item").setAs(new String[]{"minecraft:stone", "minecraft:ender_pearl"});
        counter.getTree().getProp("arcadium$itemCounterLayout").setAs(1);
        healthId = health.getTree().getID(); heightId = height.getTree().getID(); counterId = counter.getTree().getID();
        ConfigManager.active().saveAll(); ConfigManager.openProfile(previousProfile); ConfigManager.openProfile(profile);
        health = (HealthHud) HudManager.INSTANCE.instanceById(healthId); height = (HeightLimitHud) HudManager.INSTANCE.instanceById(heightId); counter = HudManager.INSTANCE.instanceById(counterId);
        require(health != null && height != null && counter != null, "HUD instances did not survive profile reload");
        require(health.getRelativeX() == 40 && height.getRelativeY() == 85 && height.getStyle() == 2, "HUD state did not survive profile reload");
        for (Hud hud : HudManager.INSTANCE.getActiveInstances()) hud.setHidden(hud != health && hud != height && hud != counter);
        report.put("savedHudReload", true);
    }

    private Hud provider(String name) { return HudManager.INSTANCE.providers().stream().filter(hud -> hud.getClass().getName().equals(name)).findFirst().orElseThrow(); }
    private static void style(Hud hud, float x, float y) {
        hud.setHidden(false); hud.setSection(Section.TopLeft); hud.setRelativeX(x); hud.setRelativeY(y); hud.setFont(Font.Minecraft);
        hud.setTextScale(1.25f); hud.setTextColor(0xFFF8CC66); hud.setShowBackground(true); hud.setBgColor(0xC0181F29);
        hud.setUseGuiScale(false); hud.setStaticWidth(false); hud.setCustomScale(1.4f); hud.setup();
    }
    private void seedPlayer() {
        minecraft.getSingleplayerServer().execute(() -> {
            var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.getUser().getProfileId());
            require(player != null, "Synthetic server player missing"); player.setHealth(17); player.getFoodData().setFoodLevel(10);
            player.getInventory().setItem(0, new ItemStack(Items.STONE, 32)); player.getInventory().setItem(1, new ItemStack(Items.ENDER_PEARL, 5));
            player.containerMenu.broadcastChanges();
        });
    }
    private void setupCanvas() {
        var design = new CrosshairDesign(); design.name = "Native26QA"; design.mode = 1; design.scale = 3;
        for (int y = 14; y <= 17; y++) for (int x = 14; x <= 17; x++) design.pixels[y * 32 + x] = 0xFF03F31B;
        CustomCrosshair.INSTANCE.update(design); CustomCrosshair.INSTANCE.setEnabled(true); CustomCrosshairConfig.setThirdPerson(true);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
    }
    private void setupAutoText() {
        for (var mapping : minecraft.options.keyMappings) if (mapping.matches(new KeyEvent(GLFW.GLFW_KEY_V, 0, 0))) {
            keys.put(mapping, mapping.saveString()); mapping.setKey(InputConstants.UNKNOWN);
        }
        KeyMapping.resetMapping(); AutoText.INSTANCE.add(); autoId = AutoText.INSTANCE.getEntries().getLast().getId();
        AutoText.INSTANCE.setText(autoId, "/say AUTO_" + marker); AutoTextConfig.setEnabled(true);
        var bind = AutoText.INSTANCE.binding(autoId).get(); bind.setKeyCodes(new int[]{GLFW.GLFW_KEY_V}); KeybindManager.notifyMenuEdit(bind);
        require(KeybindManager.isRegistered(bind), "AutoText shortcut is not registered");
    }
    private void key(int key, int action) { ((KeyboardQaAccess) minecraft.keyboardHandler).arcadium$nativeKey(minecraft.getWindow().handle(), action, new KeyEvent(key, 0, 0)); }
    private boolean chatContains(String marker) { return ((ChatQaAccess) minecraft.gui.hud.getChat()).arcadium$messages().stream().anyMatch(message -> message.content().getString().contains(marker)); }
    private static String text(Hud hud) {
        if (hud instanceof ColoredTextHud) try {
            var getter = ColoredTextHud.class.getDeclaredMethod("getText"); getter.setAccessible(true); return (String) getter.invoke(hud);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        var values = new ArrayList<String>(); texts(hud.getRuntime().getRoot(), values); return String.join("", values);
    }
    private static void texts(PolyNode node, List<String> values) {
        if (node instanceof TextNode text) values.add(text.getText()); else if (node instanceof McTextNode text) values.add(text.getText());
        for (PolyNode child : node.getChildren()) texts(child, values);
    }
    private static Map<String, Object> hudState(Hud hud) {
        var state = new LinkedHashMap<String, Object>();
        state.put("class", hud.getClass().getName()); state.put("id", hud.getTree().getID());
        state.put("active", HudManager.INSTANCE.getActiveInstances().contains(hud)); state.put("hidden", hud.getHidden()); state.put("shouldShow", hud.shouldShow());
        state.put("width", hud.getRenderedW()); state.put("height", hud.getRenderedH());
        var nodes = new ArrayList<String>();
        if (hud.getRuntimeOrNull() != null) nodeTypes(hud.getRuntime().getRoot(), nodes, 0);
        state.put("nodes", nodes); return state;
    }
    private static void nodeTypes(PolyNode node, List<String> values, int depth) {
        if (depth > 12 || values.size() > 80) return;
        values.add(node.getClass().getName() + " " + node.getWidth() + "x" + node.getHeight());
        for (PolyNode child : node.getChildren()) nodeTypes(child, values, depth + 1);
    }
    private void failCapture(String name, Throwable cause) {
        waiting = true;
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (image) { image.writeToFile(output.resolve(name + ".png")); }
            catch (Throwable writing) { cause.addSuppressed(writing); }
            QaTickScheduler.execute(() -> cleanup(cause));
        });
    }

    private void capture(String name, boolean canvas) {
        waiting = true;
        if (canvas) {
            var chat = minecraft.gui.hud.getChat(); chatState = chat.storeState(); chat.clearMessages(false); chat.rescaleChat();
        }
        QaTickScheduler.afterFrames(5).whenCompleteAsync((ignored, failed) -> {
            if (failed != null) { cleanup(failed); return; }
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
                try (image) {
                    image.writeToFile(output.resolve(name + ".png"));
                    var capture = new LinkedHashMap<String, Object>(); capture.put("image", name + ".png");
                    if (name.equals("world-first-person")) {
                        var healthProof = hudPixels(image, health);
                        require(((Number) healthProof.get("heartPixels")).intValue() >= 4 && ((Number) healthProof.get("textPixels")).intValue() >= 8,
                                "Native Health HUD heart/number pixels are missing: " + healthProof);
                        capture.put("healthPixels", healthProof);
                        report.put("healthPixels", healthProof);
                    }
                    if (canvas) {
                        int matching = 0;
                        for (int y = image.getHeight()/2-30; y < image.getHeight()/2+30; y++) for (int x = image.getWidth()/2-30; x < image.getWidth()/2+30; x++) {
                            int rgb = image.getPixel(x,y), red = rgb>>>16&255, green = rgb>>>8&255, blue = rgb&255;
                            if (green >= 32 && green >= Math.max(red,blue)*4+8) matching++;
                        }
                        if (canvasPixels == 0) canvasPixels = matching;
                        require(matching >= 16 && matching == canvasPixels, "Canvas pixels differ between native camera modes: " + matching + " vs " + canvasPixels);
                        capture.put("canvasPixels", matching);
                    }
                    captures.add(capture);
                    QaTickScheduler.execute(() -> { restoreChat(); waiting = false; next(); });
                } catch (Throwable cause) { QaTickScheduler.execute(() -> cleanup(cause)); }
            });
        }, QaTickScheduler.EXECUTOR);
    }
    private static Map<String, Object> hudPixels(com.mojang.blaze3d.platform.NativeImage image, Hud hud) {
        float sx = image.getWidth() / HudManager.guiScreenWidth, sy = image.getHeight() / HudManager.guiScreenHeight;
        int left = Math.max(0, (int) Math.floor(hud.getX() * sx)), top = Math.max(0, (int) Math.floor(hud.getY() * sy));
        int right = Math.min(image.getWidth(), (int) Math.ceil((hud.getX() + hud.getRenderedW()) * sx));
        int bottom = Math.min(image.getHeight(), (int) Math.ceil((hud.getY() + hud.getRenderedH()) * sy));
        int heart = 0, text = 0;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            int pixel = image.getPixel(x, y), red = pixel >>> 16 & 255, green = pixel >>> 8 & 255, blue = pixel & 255;
            if (Math.abs(red - 255) <= 35 && Math.abs(green - 85) <= 35 && Math.abs(blue - 85) <= 35) heart++;
            if (Math.abs(red - 248) <= 35 && Math.abs(green - 204) <= 35 && Math.abs(blue - 102) <= 35) text++;
        }
        return Map.of("heartPixels", heart, "textPixels", text, "bounds", List.of(left, top, right, bottom),
                "hudDimensions", List.of(hud.getRenderedW(), hud.getRenderedH()), "hudSemanticText", text(hud));
    }
    private void restoreChat() { if (chatState != null) { var chat=minecraft.gui.hud.getChat(); chat.restoreState(chatState); chat.rescaleChat(); chatState=null; } }
    private void next() { phase++; ticks = 0; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private void cleanup(Throwable cause) {
        if (complete || cleaning) return;
        failure = cause; waiting = false; cleaning = true; closingAt = System.nanoTime(); closingServer = minecraft.getSingleplayerServer();
        try {
            restoreChat(); key(GLFW.GLFW_KEY_V, GLFW.GLFW_RELEASE); key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
            for (var entry : keys.entrySet()) entry.getKey().setKey(InputConstants.getKey(entry.getValue())); KeyMapping.resetMapping();
            if (autoId != null) AutoText.INSTANCE.remove(autoId); AutoTextConfig.setEnabled(previousAutoText);
            CustomCrosshair.INSTANCE.update(previousCrosshair); CustomCrosshair.INSTANCE.setEnabled(previousEnabled); CustomCrosshairConfig.setThirdPerson(previousThirdPerson);
            minecraft.options.setCameraType(previousCamera); minecraft.options.renderDistance().set(previousDistance);
            ConfigManager.active().saveAll(); ConfigManager.openProfile(previousProfile);
            if (minecraft.level != null) minecraft.disconnectFromWorld(Component.translatable("menu.savingLevel"));
            minecraft.gui.setScreen(new ArcadiumMainMenu());
        } catch (Throwable restoring) { if (failure == null) failure = restoring; finish(); }
    }
    private void pollCleanup() {
        boolean stopped = closingServer == null || closingServer.isStopped();
        boolean threadAlive = closingServer != null && closingServer.getRunningThread().isAlive();
        if (stopped && !threadAlive && minecraft.level == null) finish();
        else if (System.nanoTime() - closingAt > 60_000_000_000L) { if (failure == null) failure = new IllegalStateException("Native world save/disconnect did not complete"); finish(); }
    }
    private void finish() {
        if (complete) return; complete = true;
        if (failure == null && worldId != null) {
            var metadata = io.github.arcadiumclient.modern.gui.WorldMetadata.read(minecraft.getLevelSource().getBaseDir().resolve(worldId));
            if (!metadata.readable() || !metadata.name().equals(worldName) || metadata.newerThan("26.2", net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()))
                failure = new IllegalStateException("Saved26.2world metadata did not round-trip into the Arcadium world browser: " + metadata);
            report.put("savedWorldMetadata", Map.of("readable", metadata.readable(), "name", metadata.name(), "version", metadata.versionName(), "dataVersion", metadata.dataVersion()));
        }
        report.put("world", worldName); report.put("synthetic", true); report.put("screenshots", captures);
        report.put("restored", minecraft.level == null && ConfigManager.activeProfile().equals(previousProfile));
        report.put("nativeServerStopped", closingServer == null || closingServer.isStopped());
        try { if (!report.containsKey("graphicsBackend")) report.putAll(NativeGraphicsQa.snapshot()); }
        catch (Throwable graphicsFailure) { if (failure == null) failure = graphicsFailure; }
        report.put("passed", failure == null);
        if (failure != null) report.put("error", failure.toString());
        try { Files.writeString(output.resolve("play.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Throwable writing) { if (failure == null) failure = writing; }
        if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
    }
}
