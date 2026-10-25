// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.arcadiumclient.modern.client.features.AutoText;
import io.github.arcadiumclient.modern.client.features.AutoTextConfig;
import io.github.arcadiumclient.modern.client.features.CrosshairDesign;
import io.github.arcadiumclient.modern.client.features.CustomCrosshair;
import io.github.arcadiumclient.modern.client.features.CustomCrosshairConfig;
import io.github.arcadiumclient.modern.client.features.HealthHud;
import io.github.arcadiumclient.modern.client.features.HeightLimitHud;
import io.github.arcadiumclient.modern.client.features.ItemCounterRows;
import io.github.arcadiumclient.modern.gui.ArcadiumCreateWorldScreen;
import io.github.arcadiumclient.modern.gui.ArcadiumMainMenu;
import io.github.arcadiumclient.modern.gui.ArcadiumResourcePacksScreen;
import io.github.arcadiumclient.modern.gui.qa.mixin.ChatQaAccess;
import io.github.arcadiumclient.modern.gui.qa.mixin.KeyboardQaAccess;
import io.github.arcadiumclient.modern.gui.qa.mixin.MouseQaAccess;
import io.github.arcadiumclient.modern.mixin.gui.CreateWorldAccess;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.lwjgl.glfw.GLFW;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.hud.v1.Font;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.Section;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager;
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus;
import org.polyfrost.oneconfig.internal.ui.keybind.KeybindRecordingBus;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.KeyInputEvent;
import org.polyfrost.oneconfig.api.event.v1.events.ChatEvent;
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.polyfrost.oneconfig.api.notifications.v1.Notification;
import org.polyfrost.oneconfig.api.notifications.v1.Notifications;
import org.polyfrost.oneconfig.api.notifications.v1.NotificationsManager;
import org.polyfrost.oneconfig.internal.FontPreferences;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.polyfrost.compose.node.PolyNode;
import org.polyfrost.compose.node.TextNode;
import org.polyfrost.compose.node.McTextNode;

final class ModernPlayQa {
    private static final String HEALTH_CLASS = "io.github.arcadiumclient.runtime.client.features.HealthHud";
    private static final String HEIGHT_CLASS = "io.github.arcadiumclient.runtime.client.features.HeightLimitHud";
    private static final String COUNTER_CLASS = "org.polyfrost.evergreenhud.client.hud.ItemCounterHud";
    private final Minecraft minecraft;
    private final Path output;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> screenshots = new ArrayList<>();
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> nativeKeyEvents = new ArrayList<>();
    private final List<Map<String, Object>> bindingSnapshots = new ArrayList<>();
    private final List<Map<String, Object>> pointerTrace = new ArrayList<>();
    private final List<String> failedAssertions = new ArrayList<>();
    private final Map<KeyMapping, String> temporaryKeyChanges = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final String marker = UUID.randomUUID().toString().substring(0, 8);
    private final String worldName = "Arcadium Play QA " + marker;
    private final String chatMarker = "ARCADIUM_CHAT_QA_" + marker;
    private final String autoMarker = "ARCADIUM_AUTOTEXT_QA_" + marker;
    private final String voiceMarker = "ARCADIUM_VOICECHAT_THREAD_QA_" + marker;
    private final String profileName = "Arcadium Play QA " + marker;
    private final CrosshairDesign previousCrosshair = CustomCrosshair.design().copy();
    private final boolean previousCrosshairEnabled = CustomCrosshair.INSTANCE.enabled();
    private final boolean previousThirdPerson = CustomCrosshairConfig.getThirdPerson();
    private final CameraType previousCamera;
    private final int previousRenderDistance;
    private final boolean previousAutoText = AutoTextConfig.getEnabled();
    private final double previousChatOpacity;
    private final ChatVisiblity previousChatVisibility;
    private final String previousProfile = ConfigManager.activeProfile();
    private final List<String> previousPacks;
    private final List<String> previousOptionPacks;
    private final List<String> previousIncompatible;
    private final boolean[] previousFonts = {OneConfigConfig.useVanillaFontInGame, OneConfigConfig.useInterInGame,
            OneConfigConfig.useVanillaFontForModText, OneConfigConfig.useInterForModText};
    private final List<Map<String, Object>> compositeFonts = new ArrayList<>();
    private final boolean[] previousNotifications = {OneConfigConfig.enableNotifications, OneConfigConfig.enableNotificationSounds, OneConfigConfig.enableUISounds};
    private final float previousUiVolume = OneConfigConfig.uiSoundVolume;
    private AbstractAppender errorCapture;
    private EventHandler<KeyInputEvent> keyCapture;
    private EventHandler<ChatEvent.Receive> chatCapture;
    private final List<Map<String, Object>> voiceThreads = new ArrayList<>();
    private CompletableFuture<Void> voiceWorker;
    private boolean optionsOpened;
    private boolean pauseVerified;
    private HealthHud health;
    private HeightLimitHud height;
    private Hud counter;
    private Hud composite;
    private Notification notification;
    private String healthId;
    private String heightId;
    private String counterId;
    private String worldId;
    private String autoId;
    private int phase;
    private int ticks;
    private int sentTick;
    private int totalTicks;
    private boolean waiting;
    private boolean complete;
    private boolean cleaning;
    private boolean resourcesRestored;
    private boolean disconnectComplete;
    private IntegratedServer cleanupServer;
    private long cleanupStarted;
    private int cleanupTicks;
    private ChatIsolation chatIsolation;
    private record ChatIsolation(ChatComponent.State nativeState, int messages, long frame) {}
    private Throwable failure;
    private List<String> packOrder;
    private boolean packDropDone;
    private int pointerStage;
    private int pointerMoves;
    private int legacyItemStage;
    private float pointerX;
    private float pointerStartY;
    private float pointerEndY;
    private float horizontalWidth;
    private float horizontalHeight;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        try {
            require(minecraft.level == null && minecraft.getSingleplayerServer() == null, "Play QA must start outside a world");
            ModernPlayQa qa = new ModernPlayQa(minecraft, output);
            qa.start();
            return qa.completion;
        } catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    }

    private ModernPlayQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft;
        this.output = output;
        previousCamera = minecraft.options.getCameraType();
        previousRenderDistance = minecraft.options.renderDistance().get();
        previousChatOpacity = minecraft.options.chatOpacity().get();
        previousChatVisibility = minecraft.options.chatVisibility().get();
        previousPacks = List.copyOf(minecraft.getResourcePackRepository().getSelectedIds());
        previousOptionPacks = List.copyOf(minecraft.options.resourcePacks);
        previousIncompatible = List.copyOf(minecraft.options.incompatibleResourcePacks);
    }

    private void start() throws Exception {
        Files.createDirectories(output);
        report.put("world", worldName);
        report.put("synthetic", true);
        report.put("failedAssertions", failedAssertions);
        installErrorCapture();
        report.put("nativeKeyEvents", nativeKeyEvents);
        report.put("autoTextBindingSnapshots", bindingSnapshots);
        report.put("pointerTrace", pointerTrace);
        keyCapture = EventManager.register(KeyInputEvent.class, event -> {
            if (nativeKeyEvents.size() < 80) nativeKeyEvents.add(Map.of("key", event.key, "state", event.state,
                    "phase", phase, "screen", minecraft.screen == null ? "null" : minecraft.screen.getClass().getName(), "typing", TextInputFocus.isTyping()));
        });
        chatCapture = EventManager.register(ChatEvent.Receive.class, event -> {
            if (PlainTextComponentSerializer.plainText().serialize(event.getMessage()).contains(voiceMarker))
                voiceThreads.add(Map.of("thread", Thread.currentThread().getName(), "clientThread", minecraft.isSameThread()));
        });
        if (Boolean.getBoolean("arcadium.guiQa.packsOnly")) {
            report.put("packsOnly", true);
            report.put("syntheticWorldCreated", false);
            phase = 11;
        } else {
            report.put("syntheticWorldCreated", true);
            minecraft.options.renderDistance().set(Boolean.getBoolean("arcadium.guiQa.benchmark") ? 8 : 4);
            CreateWorldScreen.openFresh(minecraft, () -> minecraft.setScreen(new ArcadiumMainMenu()));
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (complete) return;
            if (cleaning) {
                try { pollCleanup(); }
                catch (Throwable cleanupFailure) { if (failure == null) failure = cleanupFailure; finish(); }
                return;
            }
            if (System.nanoTime() - started > 180_000_000_000L) {
                failure = new IllegalStateException("Play QA exceeded its 180 second limit in phase " + phase);
                waiting = false; cleanup(failure);
                return;
            }
            if (waiting) return;
            try { tick(); } catch (Throwable failure) { fail(failure); }
        });
        progress();
    }

    private void tick() throws Exception {
        if (++totalTicks > 3600 || System.nanoTime() - started > 180_000_000_000L) throw new IllegalStateException("Play QA timed out in phase " + phase);
        if (minecraft.getOverlay() != null) return;
        ticks++;
        switch (phase) {
            case 0 -> {
                if (!(minecraft.screen instanceof ArcadiumCreateWorldScreen)) return;
                CreateWorldScreen nativeScreen = (CreateWorldScreen) field(minecraft.screen, "delegate");
                var state = nativeScreen.getUiState();
                state.setName(worldName);
                state.setGameMode(SelectedGameMode.CREATIVE);
                state.setAllowCommands(true);
                state.setGenerateStructures(false);
                state.setBonusChest(false);
                state.setSeed("16438450");
                var flat = state.getNormalPresetList().stream().filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow();
                state.setWorldType(flat);
                worldId = state.getTargetFolder();
                require(minecraft.getLevelSource().getLevelPath(worldId).toAbsolutePath().normalize().startsWith(minecraft.getLevelSource().getBaseDir().toAbsolutePath().normalize()), "Synthetic world path escaped saves");
                ((CreateWorldAccess) nativeScreen).arcadium$create();
                next();
            }
            case 1 -> {
                if (minecraft.level == null || minecraft.player == null || !minecraft.hasSingleplayerServer()) return;
                minecraft.setScreen(null);
                if (ticks < 40) return;
                require(minecraft.getSingleplayerServer().getWorldData().getLevelName().equals(worldName), "Loaded world is not the synthetic QA world");
                if (Boolean.getBoolean("arcadium.guiQa.pauseOnly")) {
                    waiting = true;
                    report.put("scope", "pause-only");
                    ModernPauseQa.run(minecraft, output).whenComplete((pause, pauseFailure) -> QaTickScheduler.execute(() -> {
                        waiting = false;
                        if (pauseFailure != null) fail(pauseFailure);
                        else { report.put("pauseConfirmation", pause); cleanup(null); }
                    }));
                    return;
                }
                if (Boolean.getBoolean("arcadium.guiQa.chatInputOnly")) {
                    waiting = true;
                    report.put("scope", "chat-input-only");
                    ModernChatInputQa.run(minecraft, output).whenComplete((input, inputFailure) -> QaTickScheduler.execute(() -> {
                        waiting = false;
                        if (inputFailure != null) fail(inputFailure);
                        else { report.put("chatInput", input); cleanup(null); }
                    }));
                    return;
                }
                if (Boolean.getBoolean("arcadium.guiQa.benchmark")) {
                    waiting = true;
                    ModernBenchmarkQa.run(minecraft, output).whenComplete((benchmark, benchmarkFailure) -> QaTickScheduler.execute(() -> {
                        waiting = false;
                        if (benchmarkFailure != null) fail(benchmarkFailure);
                        else { report.put("benchmark", benchmark); cleanup(null); }
                    }));
                    return;
                }
                if (Boolean.getBoolean("arcadium.guiQa.glintOnly")) {
                    runGlint(true);
                    return;
                }
                if (Boolean.getBoolean("arcadium.guiQa.runtimeEffectsOnly")) {
                    runEffects(true);
                    return;
                }
                if (Boolean.getBoolean("arcadium.guiQa.idleOnly")) {
                    runIdle(true);
                    return;
                }
                if (Boolean.getBoolean("arcadium.guiQa.skinsOnly")) {
                    runSkins(true);
                    return;
                }
                configureProviders();
                seedPlayer();
                configureCrosshair();
                next();
            }
            case 2 -> {
                if (ticks < 20) return;
                verifySavedHuds();
                if (Boolean.getBoolean("arcadium.guiQa.legacyItems") && legacyItemStage == 0) {
                    counter.getTree().getProp("item").setAs(new String[]{"minecraft:wool"});
                    counter.update(); legacyItemStage = 1; ticks = 0;
                    return;
                }
                if (Boolean.getBoolean("arcadium.guiQa.legacyItems") && legacyItemStage == 1) {
                    var legacyRows = ItemCounterRows.recompute(counter, new String[]{"minecraft:wool"});
                    require(legacyRows.size() == 1 && legacyRows.getFirst().getText().equals("15"), "The raw legacy wool query did not count both white8 and red7 stacks as one15 row");
                    var labels = new ArrayList<String>();
                    collectTextValues(counter.getRuntime().getRoot(), labels);
                    require(labels.size() == 1 && labels.getFirst().equals("15"), "The actual live legacy Item Counter did not render one15 label: " + labels);
                    Object raw = counter.getTree().getProp("item").get();
                    require(raw instanceof String[] values && java.util.Arrays.equals(values, new String[]{"minecraft:wool"}), "Legacy Item Counter rewrote the user's saved query");
                    ConfigManager.active().save(counterId);
                    Path savedPath = ConfigManager.active().getFolder().resolve(counterId);
                    var saved = JsonParser.parseString(Files.readString(savedPath)).getAsJsonObject().getAsJsonArray("item");
                    require(saved.size() == 1 && saved.get(0).getAsString().equals("minecraft:wool"), "Saved legacy wool query changed after a native update");
                    Files.copy(savedPath, output.resolve("saved-huds").resolve("legacy-wool-" + savedPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    report.put("legacyItemQuery", Map.of("savedQuery", "minecraft:wool", "nativeInventory", Map.of("minecraft:white_wool", 8, "minecraft:red_wool", 7),
                            "nativeRows", legacyRows.size(), "nativeLabel", labels.getFirst(), "rawQueryPreservedOnDisk", true));
                    capture("world-legacy-wool", true, () -> {
                        counter.getTree().getProp("item").setAs(new String[]{"minecraft:stone", "minecraft:ender_pearl"});
                        counter.update(); ConfigManager.active().save(counterId);
                        legacyItemStage = 2; ticks = 0;
                    });
                    return;
                }
                var rows = ItemCounterRows.recompute(counter, new String[]{"minecraft:stone", "minecraft:ender_pearl"});
                require(rows.size() == 2, "Item Counter did not produce two rows");
                require(rows.stream().anyMatch(entry -> entry.getText().contains("32")) && rows.stream().anyMatch(entry -> entry.getText().contains("5")), "Item Counter counts did not match the synthetic inventory");
                horizontalWidth = counter.getRenderedW();
                horizontalHeight = counter.getRenderedH();
                report.put("itemCounters", rows.stream().map(entry -> Map.of("item", entry.getStack().getItem().toString(), "text", entry.getText())).toList());
                capture("world-first-person", true, this::nextUnchecked);
            }
            case 3 -> {
                if (ticks == 1) minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (ticks >= 20) capture("world-third-person-back", true, this::nextUnchecked);
            }
            case 4 -> {
                if (ticks == 1) minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                if (ticks >= 20) capture("world-third-person-front", true, this::nextUnchecked);
            }
            case 5 -> {
                if (ticks == 1) {
                    minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                    counter.getTree().getProp("arcadium$itemCounterLayout").setAs(0);
                    counter.update();
                }
                if (ticks >= 20) {
                    require(counter.getRenderedH() > horizontalHeight && counter.getRenderedW() < horizontalWidth, "Item Counter layout did not change measured geometry");
                    report.put("itemCounterGeometry", Map.of("horizontal", List.of(horizontalWidth, horizontalHeight), "vertical", List.of(counter.getRenderedW(), counter.getRenderedH())));
                    capture("world-counter-vertical", true, this::nextUnchecked);
                }
            }
            case 6 -> {
                configureAutoText();
                minecraft.setScreen(new ChatScreen("/say " + chatMarker, false));
                bindingSnapshot("chat-open");
                nativeKey(GLFW.GLFW_KEY_V, GLFW.GLFW_PRESS);
                next();
            }
            case 7 -> {
                if (ticks == 2) nativeKey(GLFW.GLFW_KEY_V, GLFW.GLFW_RELEASE);
                if (ticks < 12) return;
                require(!chatContains(autoMarker), "AutoText fired while typing in chat");
                sentTick = totalTicks;
                nativeKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_PRESS);
                next();
            }
            case 8 -> {
                if (ticks == 1) nativeKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
                if (!chatContains(chatMarker)) {
                    require(ticks < 120, "Native Enter chat command was not received by the integrated server");
                    return;
                }
                require(minecraft.screen == null, "Chat did not close after Enter");
                report.put("chatEnterDeliveryTicks", totalTicks - sentTick);
                sentTick = totalTicks;
                bindingSnapshot("playing-before-V");
                nativeKey(GLFW.GLFW_KEY_V, GLFW.GLFW_PRESS);
                next();
            }
            case 9 -> {
                if (ticks == 1) nativeKey(GLFW.GLFW_KEY_V, GLFW.GLFW_RELEASE);
                if (!chatContains(autoMarker)) {
                    if (ticks >= 120) {
                        autoTextDiagnostic();
                        throw new IllegalStateException("AutoText native V shortcut did not reach the integrated server");
                    }
                    return;
                }
                report.put("autoTextKeyDeliveryTicks", totalTicks - sentTick);
                report.put("typingProtection", true);
                capture("world-chat-and-autotext", true, this::nextUnchecked);
            }
            case 10 -> {
                if (!pauseVerified) {
                    waiting = true;
                    ModernPauseQa.run(minecraft, output).whenComplete((pause, pauseFailure) -> QaTickScheduler.execute(() -> {
                        waiting = false;
                        if (pauseFailure != null) fail(pauseFailure);
                        else { report.put("pauseConfirmation", pause); pauseVerified = true; ticks = 0; }
                    }));
                    return;
                }
                if (ticks == 1) {
                    voiceWorker = CompletableFuture.runAsync(() -> {
                        try {
                            Class.forName("de.maxhenkel.voicechat.voice.client.ChatUtils").getMethod("sendPlayerMessage", Component.class)
                                    .invoke(null, Component.literal(voiceMarker));
                        } catch (ReflectiveOperationException failure) { throw new java.util.concurrent.CompletionException(failure); }
                    });
                }
                if (!optionsOpened) {
                    if (!voiceWorker.isDone() || !chatContains(voiceMarker)) {
                        if (voiceWorker.isCompletedExceptionally()) voiceWorker.join();
                        require(ticks < 120, "VoiceChat worker message was not delivered to native client chat");
                        return;
                    }
                    long copies = ((ChatQaAccess) minecraft.gui.getChat()).arcadium$messages().stream().filter(message -> message.content().getString().contains(voiceMarker)).count();
                    require(copies == 1 && !voiceThreads.isEmpty() && voiceThreads.stream().allMatch(value -> Boolean.TRUE.equals(value.get("clientThread"))), "VoiceChat worker adapter did not deliver exactly one safe client-thread message");
                    report.put("voiceChatWorker", Map.of("nativeChatCopies", copies, "events", voiceThreads));
                    minecraft.options.chatOpacity().set(1d);
                    minecraft.gui.getChat().addMessage(Component.literal("ARCADIUM_OPTIONS_CHAT_QA_" + marker).withStyle(style -> style.withColor(0xFF00FF)));
                    OneConfigUI.open();
                    optionsOpened = true;
                    ticks = 0;
                    return;
                }
                if (ticks >= 25) {
                    long copies = ((ChatQaAccess) minecraft.gui.getChat()).arcadium$messages().stream().filter(message -> message.content().getString().contains("ARCADIUM_OPTIONS_CHAT_QA_" + marker)).count();
                    require(copies == 1, "Options chat fixture was duplicated in native chat history");
                    report.put("optionsChat", Map.of("marker", "ARCADIUM_OPTIONS_CHAT_QA_" + marker, "nativeHistoryCopies", copies, "image", "world-arcadium-options.png"));
                    capture("world-arcadium-options", false, this::nextUnchecked);
                }
            }
            case 11 -> {
                if (ticks == 1 && pointerStage == 0) {
                    seedPack("Play QA/Green");
                    seedPack("Play QA/Blue");
                    minecraft.getResourcePackRepository().reload();
                    minecraft.setScreen(new ArcadiumResourcePacksScreen(null));
                    var screen = minecraft.screen;
                    Class<?> view = Class.forName("io.github.arcadiumclient.modern.gui.PackView");
                    var constructor = view.getDeclaredConstructor(net.minecraft.server.packs.repository.Pack.class);
                    constructor.setAccessible(true);
                    var add = screen.getClass().getDeclaredMethod("add", view, int.class);
                    add.setAccessible(true);
                    add.invoke(screen, constructor.newInstance(minecraft.getResourcePackRepository().getPack("file/Play QA/Green")), 0);
                    add.invoke(screen, constructor.newInstance(minecraft.getResourcePackRepository().getPack("file/Play QA/Blue")), 0);
                }
                if (ticks < 10) return;
                if (!packDropDone) {
                    var screen = minecraft.screen;
                    Class<?> dragType = Class.forName("io.github.arcadiumclient.modern.gui.PackDrag");
                    var dragConstructor = dragType.getDeclaredConstructor(String.class, boolean.class);
                    dragConstructor.setAccessible(true);
                    if (pointerStage == 0) {
                        Object green = dragConstructor.newInstance("Play QA/Green", true);
                        Object blue = dragConstructor.newInstance("Play QA/Blue", true);
                        @SuppressWarnings("unchecked") var bounds = (Map<Object, androidx.compose.ui.geometry.Rect>) field(screen, "cardBounds");
                        var source = bounds.get(green);
                        var destination = bounds.get(blue);
                        var list = (androidx.compose.ui.geometry.Rect) field(screen, "activeListBounds");
                        if (source == null || destination == null || source.getWidth() <= 0 || !minecraft.isWindowActive()) {
                            require(ticks < 200, "Resource pack pointer test requires laid-out cards and an active game window");
                            return;
                        }
                        pointerX = source.getLeft() + Math.min(90, (source.getWidth() - 90) / 2);
                        float visibleTop = Math.max(source.getTop(), list.getTop()) + 3;
                        float visibleBottom = Math.min(source.getBottom(), list.getBottom()) - 3;
                        require(visibleTop < visibleBottom, "Source resource pack card has no visible drag surface");
                        pointerStartY = (visibleTop + visibleBottom) / 2;
                        pointerEndY = Math.max(destination.getTop(), list.getTop()) + 2;
                        pointerTrace.add(Map.of("event", "bounds", "source", rect(source), "target", rect(destination), "list", rect(list), "root", field(screen, "dragRoot").toString()));
                        pointerMove(pointerX, pointerStartY);
                        pointerState("move-start");
                        pointerStage = 1;
                        ticks = 0;
                        return;
                    }
                    if (pointerStage == 1) {
                        screen.mouseClicked(pointerEvent(pointerX, pointerStartY), false);
                        pointerState("press");
                        pointerStage = 2;
                        ticks = 0;
                        return;
                    }
                    if (pointerStage == 2) {
                        float previousY = pointerStartY + (pointerEndY - pointerStartY) * pointerMoves / 10f;
                        float y = pointerStartY + (pointerEndY - pointerStartY) * ++pointerMoves / 10f;
                        pointerMove(pointerX, y);
                        screen.mouseDragged(pointerEvent(pointerX, y), 0, (y - previousY) / minecraft.getWindow().getGuiScale());
                        pointerState("drag-" + pointerMoves);
                        if (pointerMoves >= 10) pointerStage = 3;
                        ticks = 7;
                        return;
                    }
                    if (pointerStage == 3) {
                        if (composeValue(screen, "dragging") == null) {
                            failedAssertions.add("Native pointer movement did not start the resource pack drag gesture");
                            screen.mouseReleased(pointerEvent(pointerX, pointerEndY));
                            capture("world-resource-pack-drag-failed", false, this::nextUnchecked);
                            return;
                        }
                        screen.mouseReleased(pointerEvent(pointerX, pointerEndY));
                        pointerStage = 4;
                        ticks = 0;
                        return;
                    }
                    @SuppressWarnings("unchecked") var active = (List<String>) composeValue(screen, "active");
                    if (active.indexOf("Play QA/Green") >= active.indexOf("Play QA/Blue")) {
                        failedAssertions.add("Resource pack reorder did not change priority");
                        capture("world-resource-pack-order-failed", false, this::nextUnchecked);
                        return;
                    }
                    packOrder = List.copyOf(active);
                    var apply = screen.getClass().getDeclaredMethod("finish", boolean.class);
                    apply.setAccessible(true);
                    apply.invoke(screen, false);
                    packDropDone = true;
                    report.put("resourcePackDrag", Map.of("source", "Play QA/Green", "pointerStart", List.of(pointerX, pointerStartY),
                            "pointerEnd", List.of(pointerX, pointerEndY), "realCardBounds", true, "nativeScreenInput", true,
                            "moves", pointerMoves, "windowActive", minecraft.isWindowActive()));
                    return;
                }
                if (ticks < 20 || Boolean.TRUE.equals(composeValue(minecraft.screen, "applying"))) return;
                var selected = new ArrayList<>(minecraft.getResourcePackRepository().getSelectedIds());
                boolean applied = selected.indexOf("file/Play QA/Green") > selected.indexOf("file/Play QA/Blue");
                if (!applied) failedAssertions.add("Applied pack priority differs from the resource pack screen");
                report.put("resourcePackOrder", packOrder);
                report.put("resourcePackApply", applied);
                capture("world-resource-packs", false, this::nextUnchecked);
            }
            case 12 -> {
                if (Boolean.getBoolean("arcadium.guiQa.packsOnly")) { cleanup(null); return; }
                if (!Boolean.getBoolean("arcadium.guiQa.fonts")) { phase = 16; ticks = 0; return; }
                minecraft.setScreen(null);
                setFontMode(0);
                composite = provider("org.polyfrost.evergreenhud.client.hud.keystrokes.KeystrokesHud").make(null);
                style(composite, 200, 59);
                composite.setFont(Font.Poppins);
                composite.getTree().getProp("mode").setAs(1);
                composite.getTree().getProp("customLabel").setAs("AAAA");
                composite.getTree().getProp("customWidth").setAs(64f);
                composite.getTree().getProp("customHeight").setAs(24f);
                HudManager.markProviderKnown(composite);
                if (!HudManager.INSTANCE.getActiveInstances().contains(composite)) {
                    HudManager.INSTANCE.getActiveInstances().add(composite);
                    var revision = HudManager.INSTANCE.getInstancesRevision();
                    revision.setIntValue(revision.getIntValue() + 1);
                    composite.setup();
                    composite.captureStaticSizeDefaults(false);
                    composite.capturePositionDefaults();
                }
                composite.update();
                next();
            }
            case 13 -> {
                if (ticks < 20) return;
                verifyCompositeFont("poppins", false);
                capture("world-composite-poppins", true, this::nextUnchecked);
            }
            case 14 -> {
                if (ticks == 1) setFontMode(1);
                if (ticks < 20) return;
                verifyCompositeFont("vanilla", true);
                capture("world-composite-vanilla", true, this::nextUnchecked);
            }
            case 15 -> {
                if (ticks == 1) setFontMode(2);
                if (ticks < 20) return;
                verifyCompositeFont("inter", true);
                report.put("compositeHudFonts", compositeFonts);
                capture("world-composite-inter", true, this::nextUnchecked);
            }
            case 16 -> {
                minecraft.setScreen(null);
                restoreFonts();
                OneConfigConfig.enableNotifications = true;
                OneConfigConfig.enableNotificationSounds = true;
                OneConfigConfig.enableUISounds = true;
                OneConfigConfig.uiSoundVolume = .5f;
                NativeSoundQa.start();
                notification = Notifications.info("Arcadium QA", "Native toast " + marker, 10_000f);
                next();
            }
            case 17 -> {
                if (ticks < 20) return;
                require(NotificationsManager.INSTANCE.getActive().contains(notification), "Enabled notification was not added to the live toast renderer");
                var sounds = NativeSoundQa.finish();
                report.put("notificationSound", sounds);
                report.put("notificationSoundDiagnostics", NativeSoundQa.diagnostics());
                if (sounds.isEmpty()) report.put("notificationFailureProbes", NativeSoundQa.failureProbes());
                require(!sounds.isEmpty(), "Enabled notification did not reach the native sound manager");
                capture("world-notification-enabled", true, this::nextUnchecked);
            }
            case 18 -> {
                if (ticks == 1) {
                    dismissNotification();
                    OneConfigConfig.enableUISounds = false;
                    NativeSoundQa.start();
                    notification = Notifications.info("Arcadium QA", "Silent toast " + marker, 10_000f);
                }
                if (ticks < 20) return;
                require(NotificationsManager.INSTANCE.getActive().contains(notification), "Muting UI sounds disabled the visible toast too");
                require(NativeSoundQa.finish().isEmpty(), "Notification ignored the master UI sound switch");
                report.put("notificationMasterMute", true);
                capture("world-notification-muted", true, this::nextUnchecked);
            }
            case 19 -> {
                if (ticks == 1) {
                    dismissNotification();
                    OneConfigConfig.enableNotifications = false;
                    OneConfigConfig.enableUISounds = true;
                    NativeSoundQa.start();
                    notification = Notifications.info("Arcadium QA", "Hidden toast " + marker, 10_000f);
                }
                if (ticks < 20) return;
                require(!NotificationsManager.INSTANCE.getActive().contains(notification), "Disabled notifications still created a visible toast");
                require(NotificationsManager.INSTANCE.getHistory().contains(notification), "Disabled toast was lost from notification history");
                require(NativeSoundQa.finish().isEmpty(), "Hidden notification still played a sound");
                report.put("notificationsDisabled", true);
                capture("world-notification-disabled", true, this::nextUnchecked);
            }
            case 20 -> {
                if (Boolean.getBoolean("arcadium.guiQa.runtimeEffects")) runEffects(false);
                else next();
            }
            case 21 -> {
                if (Boolean.getBoolean("arcadium.guiQa.glint")) runGlint(false);
                else next();
            }
            case 22 -> {
                if (Boolean.getBoolean("arcadium.guiQa.idle")) runIdle(false);
                else next();
            }
            case 23 -> {
                if (Boolean.getBoolean("arcadium.guiQa.skins")) runSkins(false);
                else cleanup(null);
            }
            case 24 -> cleanup(null);
            default -> throw new IllegalStateException("Unknown play QA phase " + phase);
        }
    }

    private void configureProviders() throws Exception {
        var providers = HudManager.INSTANCE.providers();
        List<String> names = providers.stream().map(hud -> hud.getClass().getName()).sorted().toList();
        for (String expected : List.of(HEALTH_CLASS, HEIGHT_CLASS, "io.github.arcadiumclient.runtime.client.features.ToggleSprintHud",
                COUNTER_CLASS, "org.polyfrost.evergreenhud.client.hud.FpsHud", "org.polyfrost.evergreenhud.client.hud.PingHud", "org.polyfrost.evergreenhud.client.hud.TpsHud"))
            require(names.contains(expected), "HUD provider is missing: " + expected);
        report.put("hudProviders", names);
        ConfigManager.createProfile(profileName);
        if (Boolean.getBoolean("arcadium.guiQa.zoomProfiles")) report.put("zoomProfiles", ModernZoomQa.run(profileName, previousProfile, output));
        ConfigManager.openProfile(profileName);
        Hud healthInstance = provider(HEALTH_CLASS).make(null);
        Hud heightInstance = provider(HEIGHT_CLASS).make(null);
        counter = provider(COUNTER_CLASS).make(null);
        style(healthInstance, 37, 59);
        style(heightInstance, 37, 89);
        style(counter, 37, 119);
        health = (HealthHud) healthInstance;
        height = (HeightLimitHud) heightInstance;
        health.setUnits(0);
        height.setStyle(2);
        height.setFormat("#current/#limit (#distance)");
        height.setSpaceBeforeDistance(true);
        counter.getTree().getProp("item").setAs(new String[]{"minecraft:stone", "minecraft:ender_pearl"});
        require(counter.getTree().getProp("arcadium$itemCounterLayout") != null, "Item Counter layout setting is missing");
        counter.getTree().getProp("arcadium$itemCounterLayout").setAs(1);
        healthId = health.getTree().getID();
        heightId = height.getTree().getID();
        counterId = counter.getTree().getID();
        ConfigManager.active().saveAll();
        Files.createDirectories(output.resolve("saved-huds"));
        for (Hud hud : List.of(health, height, counter)) {
            Path source = ConfigManager.active().getFolder().resolve(hud.getTree().getID());
            require(Files.isRegularFile(source), "HUD fixture was not saved: " + hud.getTree().getID());
            Files.copy(source, output.resolve("saved-huds").resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        ConfigManager.openProfile(previousProfile);
        ConfigManager.openProfile(profileName);
        health = (HealthHud) HudManager.INSTANCE.instanceById(healthId);
        height = (HeightLimitHud) HudManager.INSTANCE.instanceById(heightId);
        counter = HudManager.INSTANCE.instanceById(counterId);
        require(health != null && height != null && counter != null, "Saved HUD instances did not reload");
        for (Hud hud : HudManager.INSTANCE.getActiveInstances()) hud.setHidden(hud != health && hud != height && hud != counter);
    }

    private void verifySavedHuds() {
        require(health.getClass().getName().equals(HEALTH_CLASS) && height.getClass().getName().equals(HEIGHT_CLASS), "Legacy HUD class identity changed on reload");
        require(close(health.getRelativeX(), 37) && close(health.getRelativeY(), 59), "Health HUD position did not survive save/reload");
        require(close(height.getRelativeX(), 37) && close(height.getRelativeY(), 89), "Height HUD position did not survive save/reload");
        require(close(health.getTextScale(), 1.25f) && health.getTextColor() == 0xFFF8CC66 && health.getBgColor() == 0xC0181F29, "HUD style did not survive save/reload");
        require(height.getStyle() == 2 && height.getFormat().equals("#current/#limit (#distance)") && height.getSpaceBeforeDistance(), "Height format did not survive save/reload");
        require(health.shouldShow() && height.shouldShow(), "Live custom HUDs are unexpectedly hidden");
        for (Hud hud : List.of(health, height, counter)) require(hud.getRuntimeOrNull() != null && hud.getRenderedW() > 1 && hud.getRenderedH() > 1, "HUD renderer did not produce live geometry: " + hud.getClass().getName());
        health.update();
        height.update();
        require(hudText(health).contains("8.5"), "Health HUD did not show the integrated player's 17 HP as 8.5 hearts: " + hudText(health));
        int current = (int) Math.floor(minecraft.player.getY());
        int limit = minecraft.level.getMaxY() + 1;
        String expected = current + "/" + limit + " (" + Math.max(0, limit - current) + ")";
        require(hudText(height).equals(expected), "Height HUD did not read native world limits: " + hudText(height));
        report.put("savedHudClass", Map.of("health", health.getClass().getName(), "height", height.getClass().getName()));
        report.put("savedHudPosition", Map.of("health", List.of(health.getRelativeX(), health.getRelativeY()), "height", List.of(height.getRelativeX(), height.getRelativeY())));
        report.put("healthText", hudText(health));
        report.put("heightText", hudText(height));
        report.put("liveHudGeometry", List.of(health, height, counter).stream().map(hud -> Map.of("class", hud.getClass().getName(), "width", hud.getRenderedW(), "height", hud.getRenderedH())).toList());
    }

    private static void style(Hud hud, float x, float y) {
        hud.setHidden(false);
        hud.setSection(Section.TopLeft);
        hud.setRelativeX(x);
        hud.setRelativeY(y);
        hud.setFont(Font.Minecraft);
        hud.setTextScale(1.25f);
        hud.setTextColor(0xFFF8CC66);
        hud.setShowBackground(true);
        hud.setBgColor(0xC0181F29);
        hud.setUseGuiScale(false);
        hud.setStaticWidth(false);
        hud.setCustomScale(1.4f);
        hud.setup();
    }

    private Hud provider(String name) { return HudManager.INSTANCE.providers().stream().filter(hud -> hud.getClass().getName().equals(name)).findFirst().orElseThrow(); }

    private void seedPlayer() {
        minecraft.getSingleplayerServer().execute(() -> {
            var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.getUser().getProfileId());
            require(player != null, "Integrated server player is missing");
            player.setHealth(17f);
            player.getFoodData().setFoodLevel(10);
            player.getInventory().setItem(0, new ItemStack(Items.STONE, 32));
            player.getInventory().setItem(1, new ItemStack(Items.ENDER_PEARL, 5));
            if (Boolean.getBoolean("arcadium.guiQa.legacyItems")) {
                player.getInventory().setItem(2, new ItemStack(Items.WHITE_WOOL, 8));
                player.getInventory().setItem(3, new ItemStack(Items.RED_WOOL, 7));
            }
            player.containerMenu.broadcastChanges();
        });
    }

    private void configureCrosshair() {
        CrosshairDesign design = new CrosshairDesign();
        design.name = "QA canvas";
        design.mode = 1;
        design.scale = 3;
        for (int y = 14; y <= 17; y++) for (int x = 14; x <= 17; x++) design.pixels[y * 32 + x] = 0xFF03F31B;
        CustomCrosshair.INSTANCE.update(design);
        CustomCrosshair.INSTANCE.setEnabled(true);
        CustomCrosshairConfig.setThirdPerson(true);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        require(CustomCrosshair.isActive() && !CustomCrosshair.mesh().isEmpty(), "Saved canvas crosshair is not active");
    }

    private void configureAutoText() {
        var conflicts = new ArrayList<Map<String, Object>>();
        for (var mapping : minecraft.options.keyMappings) {
            if (mapping.getName().equals("key.voice_chat") && mapping.matches(new KeyEvent(GLFW.GLFW_KEY_V, 0, 0))) {
                temporaryKeyChanges.put(mapping, mapping.saveString());
                conflicts.add(Map.of("mapping", mapping.getName(), "key", mapping.saveString(), "temporarilyUnboundForQa", true));
                mapping.setKey(InputConstants.UNKNOWN);
            }
        }
        if (!temporaryKeyChanges.isEmpty()) KeyMapping.resetMapping();
        report.put("nativeInputConflicts", conflicts);
        AutoText.INSTANCE.add();
        autoId = AutoText.INSTANCE.getEntries().getLast().getId();
        AutoText.INSTANCE.rename(autoId, "Native local QA");
        AutoText.INSTANCE.setText(autoId, "/say " + autoMarker);
        AutoTextConfig.setEnabled(true);
        var bind = AutoText.INSTANCE.binding(autoId).get();
        bind.setKeyCodes(new int[]{GLFW.GLFW_KEY_V});
        bindingSnapshot("before-notify");
        KeybindManager.notifyMenuEdit(bind);
        bindingSnapshot("after-notify");
        require(KeybindManager.isRegistered(bind), "AutoText shortcut was not registered");
    }

    private void bindingSnapshot(String stage) {
        var bind = AutoText.INSTANCE.binding(autoId).get();
        var bridge = org.polyfrost.oneconfig.api.ui.v1.keybind.internal.MinecraftKeybindBridgeImpl.instance();
        var mirrors = new ArrayList<Map<String, Object>>();
        if (bridge != null) for (var mapping : bridge.entries()) if (bridge.bindFor(mapping) == bind)
            mirrors.add(Map.of("name", mapping.getName(), "nativeKey", mapping.saveString()));
        bindingSnapshots.add(Map.of("stage", stage, "keyCodes", bind.getKeyCodes() == null ? List.of() : java.util.Arrays.stream(bind.getKeyCodes()).boxed().toList(), "nativeMirrors", mirrors));
    }

    private void autoTextDiagnostic() {
        var bind = AutoText.INSTANCE.binding(autoId).get();
        var values = new LinkedHashMap<String, Object>();
        values.put("keyCodes", bind.getKeyCodes());
        values.put("mouseButtons", bind.getMouseBtns());
        values.put("modifiers", bind.getMods());
        values.put("firesWhileTyping", bind.getFiresWhileTyping());
        values.put("registered", KeybindManager.isRegistered(bind));
        values.put("typing", TextInputFocus.isTyping());
        values.put("recording", KeybindRecordingBus.isRecording());
        values.put("enabled", AutoTextConfig.getEnabled());
        values.put("onlyWhilePlaying", AutoTextConfig.getOnlyWhilePlaying());
        values.put("screen", minecraft.screen == null ? "null" : minecraft.screen.getClass().getName());
        values.put("entry", AutoText.INSTANCE.getEntries().stream().filter(entry -> entry.getId().equals(autoId)).findFirst().map(Object::toString).orElse("missing"));
        values.put("directDiagnosticSendAccepted", AutoText.INSTANCE.send(autoId));
        values.put("nativeKeyPassCriterion", false);
        report.put("autoTextFailureDiagnostic", values);
    }

    private void runGlint(boolean only) {
        waiting = true;
        ModernGlintQa.run(minecraft, output).whenComplete((glint, glintFailure) -> QaTickScheduler.execute(() -> {
            waiting = false;
            if (glintFailure != null) fail(glintFailure);
            else {
                report.put("glint", glint);
                if (only) cleanup(null);
                else nextUnchecked();
            }
        }));
    }

    private void runEffects(boolean only) {
        waiting = true;
        ModernEffectsQa.run(minecraft, output).whenComplete((effects, effectsFailure) -> QaTickScheduler.execute(() -> {
            waiting = false;
            if (effectsFailure != null) fail(effectsFailure);
            else {
                report.put("runtimeEffects", effects);
                if (only) cleanup(null);
                else nextUnchecked();
            }
        }));
    }

    private void runIdle(boolean only) {
        waiting = true;
        ModernIdleQa.run(minecraft, output).whenComplete((idle, idleFailure) -> QaTickScheduler.execute(() -> {
            waiting = false;
            if (idleFailure != null) fail(idleFailure);
            else {
                report.put("idle", idle);
                if (only) cleanup(null);
                else nextUnchecked();
            }
        }));
    }

    private void runSkins(boolean only) {
        waiting = true;
        ModernSkinQa.run(minecraft, output).whenComplete((skins, skinsFailure) -> QaTickScheduler.execute(() -> {
            waiting = false;
            if (skinsFailure != null) fail(skinsFailure);
            else {
                report.put("skins", skins);
                if (only) cleanup(null);
                else nextUnchecked();
            }
        }));
    }

    private static void setFontMode(int mode) {
        OneConfigConfig.useVanillaFontInGame = false;
        OneConfigConfig.useInterInGame = false;
        OneConfigConfig.useVanillaFontForModText = mode == 1;
        OneConfigConfig.useInterForModText = mode == 2;
        FontPreferences.refresh();
    }

    private void restoreFonts() {
        OneConfigConfig.useVanillaFontInGame = previousFonts[0];
        OneConfigConfig.useInterInGame = previousFonts[1];
        OneConfigConfig.useVanillaFontForModText = previousFonts[2];
        OneConfigConfig.useInterForModText = previousFonts[3];
        FontPreferences.refresh();
    }

    private void dismissNotification() {
        if (notification == null) return;
        Notifications.dismiss(notification);
        NotificationsManager.INSTANCE.removeFromHistory(notification);
        notification = null;
    }

    private void verifyCompositeFont(String mode, boolean nativeFont) {
        require(composite.getFont() == Font.Poppins, "Mod font preference changed the saved composite HUD font");
        require(composite.getRuntimeOrNull() != null && composite.getRenderedW() > 1, "Composite HUD did not render");
        var labels = new ArrayList<PolyNode>();
        collectLabels(composite.getRuntime().getRoot(), labels);
        require(labels.size() == 1, "Expected one live composite HUD label, got " + labels.size());
        PolyNode node = labels.getFirst();
        require(nativeFont ? node instanceof McTextNode : node instanceof TextNode,
                "Composite HUD did not select the requested renderer for " + mode + ": " + node.getClass().getName());
        require(node.getWidth() > 0 && node.getHeight() > 0, "Composite label was not measured");
        compositeFonts.add(Map.of("mode", mode, "savedFont", composite.getFont().name(), "node", node.getClass().getName(),
                "width", node.getWidth(), "height", node.getHeight()));
    }

    private static void collectLabels(PolyNode node, List<PolyNode> labels) {
        if (node instanceof TextNode text && text.getText().equals("AAAA") || node instanceof McTextNode nativeText && nativeText.getText().equals("AAAA")) labels.add(node);
        for (PolyNode child : node.getChildren()) collectLabels(child, labels);
    }

    private static void collectTextValues(PolyNode node, List<String> labels) {
        if (node instanceof TextNode text) labels.add(text.getText());
        else if (node instanceof McTextNode text) labels.add(text.getText());
        for (PolyNode child : node.getChildren()) collectTextValues(child, labels);
    }

    private void nativeKey(int key, int action) {
        ((KeyboardQaAccess) minecraft.keyboardHandler).arcadium$nativeKey(minecraft.getWindow().handle(), action, new KeyEvent(key, 0, 0));
    }

    private void pointerMove(float x, float y) {
        ((MouseQaAccess) minecraft.mouseHandler).arcadium$nativeMove(minecraft.getWindow().handle(), x, y);
    }

    private MouseButtonEvent pointerEvent(float x, float y) {
        double scale = minecraft.getWindow().getGuiScale();
        return new MouseButtonEvent(x / scale, y / scale, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
    }

    private void pointerState(String event) throws Exception {
        var value = new LinkedHashMap<String, Object>();
        value.put("event", event);
        value.put("nativeMouse", List.of(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos()));
        value.put("windowActive", minecraft.isWindowActive());
        value.put("dragging", String.valueOf(composeValue(minecraft.screen, "dragging")));
        value.put("dragPosition", String.valueOf(composeValue(minecraft.screen, "dragPosition")));
        pointerTrace.add(value);
    }

    private static List<Float> rect(androidx.compose.ui.geometry.Rect value) { return List.of(value.getLeft(), value.getTop(), value.getRight(), value.getBottom()); }

    private boolean chatContains(String text) {
        return ((ChatQaAccess) minecraft.gui.getChat()).arcadium$messages().stream().anyMatch(message -> message.content().getString().contains(text));
    }

    private void seedPack(String relative) throws Exception {
        Path directory = minecraft.getResourcePackDirectory().resolve(relative);
        Files.createDirectories(directory);
        var format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        var metadata = Map.of("pack", Map.of("description", relative, "min_format", List.of(format.major(), format.minor()), "max_format", List.of(format.major(), format.minor())));
        Files.writeString(directory.resolve("pack.mcmeta"), new GsonBuilder().create().toJson(metadata));
    }

    private void capture(String name, boolean crosshair, Runnable then) {
        waiting = true;
        if (crosshair) {
            ChatIsolation isolated = isolateChat();
            QaTickScheduler.afterFrames(5).whenCompleteAsync((ignored, failure) -> {
                if (failure != null) {
                    restoreChat(isolated);
                    waiting = false; fail(failure);
                } else try { captureFrame(name, true, then, isolated); }
                catch (Throwable captureFailure) {
                    restoreChat(isolated);
                    waiting = false; fail(captureFailure);
                }
            }, QaTickScheduler.EXECUTOR);
        } else captureFrame(name, false, then, null);
    }

    private void captureFrame(String name, boolean crosshair, Runnable then, ChatIsolation restoreState) {
        if (crosshair) require(((ChatQaAccess) minecraft.gui.getChat()).arcadium$messages().isEmpty(), "New chat arrived during isolated crosshair measurement");
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(name + ".png"));
                int matching = 0;
                int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
                if (crosshair) for (int y = image.getHeight() / 2 - 30; y < image.getHeight() / 2 + 30; y++) for (int x = image.getWidth() / 2 - 30; x < image.getWidth() / 2 + 30; x++) {
                    int rgb = image.getPixel(x, y);
                    int red = (rgb >>> 16) & 255;
                    int green = (rgb >>> 8) & 255;
                    int blue = rgb & 255;
                    if (green >= 32 && green >= Math.max(red, blue) * 4 + 8) {
                        matching++;
                        minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    }
                }
                var data = new LinkedHashMap<String, Object>();
                data.put("image", name + ".png");
                data.put("canvasPixels", matching);
                data.put("centerPixel", Integer.toHexString(image.getPixel(image.getWidth() / 2, image.getHeight() / 2)));
                data.put("camera", minecraft.options.getCameraType().name());
                data.put("nativeChatVisibility", minecraft.options.chatVisibility().get().name());
                if (restoreState != null) {
                    data.put("nativeChatHistoryTemporarilyCleared", restoreState.messages());
                    data.put("nativeFramesAfterChatClear", ModernFrameSampler.renderedFrames() - restoreState.frame());
                }
                screenshots.add(data);
                if (name.equals("world-arcadium-options")) {
                    int foreground = 0;
                    for (int y = image.getHeight() * 45 / 100; y < image.getHeight() * 95 / 100; y++)
                        for (int x = 0; x < image.getWidth() * 65 / 100; x++) {
                            int pixel = image.getPixel(x, y);
                            if ((pixel >>> 16 & 255) > 190 && (pixel & 255) > 190 && (pixel >>> 8 & 255) < 100) foreground++;
                        }
                    data.put("chatFixtureSourceArgb", "ffff00ff");
                    data.put("crispMagentaChatPixels", foreground);
                    boolean readable = foreground >= 100;
                    data.put("foregroundChatPassed", readable);
                    if (!readable) failedAssertions.add("Options chat was not rendered as readable foreground text: " + foreground + " saturated marker pixels");
                }
                if (crosshair) {
                    int expected = Math.round(4 * CustomCrosshair.design().scale * minecraft.getWindow().getGuiScale());
                    data.put("canvasSourceArgb", "ff03f31b");
                    data.put("expectedCanvasSide", expected);
                    data.put("canvasBounds", List.of(minX, minY, maxX + 1, maxY + 1));
                    data.put("canvasCoverage", matching / (double) (expected * expected));
                    require(matching >= expected * expected * .8 && matching <= expected * expected * 1.35 &&
                            Math.abs(maxX - minX + 1 - expected) <= 2 && Math.abs(maxY - minY + 1 - expected) <= 2 &&
                            Math.abs((minX + maxX + 1) / 2d - image.getWidth() / 2d) <= 1.5 &&
                            Math.abs((minY + maxY + 1) / 2d - image.getHeight() / 2d) <= 1.5,
                            "Canvas crosshair geometry/coverage differs from its native scale in " + name + ": " + data);
                }
                int frameWidth = image.getWidth(), frameHeight = image.getHeight();
                int[] originalPixels = new int[frameWidth * frameHeight];
                for (int y = 0; y < frameHeight; y++) for (int x = 0; x < frameWidth; x++) originalPixels[y * frameWidth + x] = image.getPixel(x, y);
                int canvasSide = crosshair ? ((Number) data.get("expectedCanvasSide")).intValue() : 0;
                QaTickScheduler.execute(() -> {
                    BackbufferQa.capture(output.resolve(name + "-presented.png"), presented -> {
                        require(presented.getWidth() == frameWidth && presented.getHeight() == frameHeight, "Window dimensions changed between native and presented capture");
                        return PresentedWorldProof.inspect(presented, originalPixels, name, marker, canvasSide);
                    }).whenCompleteAsync((proof, failure) -> {
                        try {
                            if (proof != null) data.put("presented", proof);
                            if (restoreState != null) restoreChat(restoreState);
                            waiting = false;
                            if (failure != null) fail(failure); else then.run();
                        } catch (Throwable captureFailure) { waiting = false; fail(captureFailure); }
                    }, QaTickScheduler.EXECUTOR);
                });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> {
                if (restoreState != null) restoreChat(restoreState);
                waiting = false; fail(failure);
            }); }
        });
    }

    private ChatIsolation isolateChat() {
        var chat = minecraft.gui.getChat();
        minecraft.getChatListener().flushQueue();
        var state = new ChatIsolation(chat.storeState(), ((ChatQaAccess) chat).arcadium$messages().size(), ModernFrameSampler.renderedFrames());
        chatIsolation = state;
        chat.clearMessages(false);
        chat.rescaleChat();
        require(((ChatQaAccess) chat).arcadium$messages().isEmpty(), "The native chat clear operation did not clear synthetic history");
        HudManager.invalidate();
        return state;
    }

    private void restoreChat(ChatIsolation state) {
        if (state == null) return;
        var chat = minecraft.gui.getChat();
        var arrived = List.copyOf(((ChatQaAccess) chat).arcadium$messages());
        chat.restoreState(state.nativeState());
        if (!arrived.isEmpty()) ((ChatQaAccess) chat).arcadium$messages().addAll(0, arrived);
        chat.rescaleChat();
        require(((ChatQaAccess) chat).arcadium$messages().size() == state.messages() + arrived.size(), "Synthetic chat history was not restored exactly");
        HudManager.invalidate();
        if (chatIsolation == state) chatIsolation = null;
    }

    private void next() throws Exception { if (complete) return; phase++; ticks = 0; progress(); }
    private void nextUnchecked() { try { next(); } catch (Throwable failure) { fail(failure); } }

    private void fail(Throwable failure) {
        if (complete) return;
        this.failure = failure;
        failure.printStackTrace();
        if (waiting) { waiting = false; cleanup(failure); return; }
        waiting = true;
        String name = "play-failure-phase-" + phase + ".png";
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {
            try (image) { image.writeToFile(output.resolve(name)); report.put("failureImage", name); }
            catch (Throwable captureFailure) { failure.addSuppressed(captureFailure); }
            QaTickScheduler.execute(() -> { waiting = false; cleanup(failure); });
        });
    }

    private void cleanup(Throwable cause) {
        if (complete || waiting) return;
        waiting = true;
        cleaning = true;
        cleanupStarted = System.nanoTime();
        cleanupServer = minecraft.getSingleplayerServer();
        if (cause != null && failure == null) failure = cause;
        try {
            restoreChat(chatIsolation);
            nativeKey(GLFW.GLFW_KEY_V, GLFW.GLFW_RELEASE);
            nativeKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
            for (var entry : temporaryKeyChanges.entrySet()) entry.getKey().setKey(InputConstants.getKey(entry.getValue()));
            if (!temporaryKeyChanges.isEmpty()) KeyMapping.resetMapping();
            if (autoId != null) AutoText.INSTANCE.remove(autoId);
            AutoTextConfig.setEnabled(previousAutoText);
            CustomCrosshair.INSTANCE.update(previousCrosshair);
            CustomCrosshair.INSTANCE.setEnabled(previousCrosshairEnabled);
            CustomCrosshairConfig.setThirdPerson(previousThirdPerson);
            restoreFonts();
            dismissNotification();
            NativeSoundQa.finish();
            OneConfigConfig.enableNotifications = previousNotifications[0];
            OneConfigConfig.enableNotificationSounds = previousNotifications[1];
            OneConfigConfig.enableUISounds = previousNotifications[2];
            OneConfigConfig.uiSoundVolume = previousUiVolume;
            minecraft.options.setCameraType(previousCamera);
            minecraft.options.renderDistance().set(previousRenderDistance);
            minecraft.options.chatOpacity().set(previousChatOpacity);
            minecraft.options.chatVisibility().set(previousChatVisibility);
            ConfigManager.active().saveAll();
            ConfigManager.openProfile(previousProfile);
            minecraft.getResourcePackRepository().setSelected(previousPacks);
            minecraft.options.resourcePacks.clear();
            minecraft.options.resourcePacks.addAll(previousOptionPacks);
            minecraft.options.incompatibleResourcePacks.clear();
            minecraft.options.incompatibleResourcePacks.addAll(previousIncompatible);
            minecraft.options.save();
            if (minecraft.level != null) minecraft.disconnectFromWorld(Component.translatable("menu.savingLevel"));
            minecraft.setScreen(new ArcadiumMainMenu());
            minecraft.reloadResourcePacks().whenComplete((ignored, restoreFailure) -> QaTickScheduler.execute(() -> {
                if (restoreFailure != null && this.failure == null) this.failure = restoreFailure;
                resourcesRestored = true;
            }));
        } catch (Throwable cleanupFailure) {
            if (this.failure == null) this.failure = cleanupFailure;
            finish();
        }
    }

    private void pollCleanup() throws Exception {
        long elapsed = System.nanoTime() - cleanupStarted;
        Thread serverThread = cleanupServer == null ? null : cleanupServer.getRunningThread();
        boolean serverStopped = cleanupServer == null || cleanupServer.isStopped();
        boolean threadAlive = serverThread != null && serverThread.isAlive();
        int savingWorlds = 0;
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("fastquit"))
            savingWorlds = ((Map<?, ?>) Class.forName("me.contaria.fastquit.FastQuit").getField("savingWorlds").get(null)).size();
        var state = new LinkedHashMap<String, Object>();
        state.put("clientLevelAbsent", minecraft.level == null);
        state.put("resourcesRestored", resourcesRestored);
        state.put("nativeServerStopped", serverStopped);
        state.put("nativeServerThreadAlive", threadAlive);
        state.put("fastQuitSavingWorlds", savingWorlds);
        state.put("elapsedMs", elapsed / 1_000_000L);
        state.put("clientTicksContinued", ++cleanupTicks);
        if (serverThread != null) state.put("serverThreadState", serverThread.getState().name());
        if (threadAlive && elapsed > 10_000_000_000L)
            state.put("serverStack", java.util.Arrays.stream(serverThread.getStackTrace()).limit(100).map(Object::toString).toList());
        report.put("nativeDisconnect", state);
        if (cleanupTicks % 20 == 1) Files.writeString(output.resolve("cleanup-progress.json"), new GsonBuilder().setPrettyPrinting().create().toJson(state));
        if (resourcesRestored && minecraft.level == null && serverStopped && !threadAlive && savingWorlds == 0) {
            disconnectComplete = true;
            finish();
        } else if (elapsed > 60_000_000_000L) {
            if (failure == null) failure = new IllegalStateException("Native integrated-server save/disconnect did not finish within60seconds while client ticks continued: " + state);
            finish();
        }
    }

    private void finish() {
        if (complete) return;
        try {
            report.put("phase", phase);
            report.put("screenshots", screenshots);
            report.put("runtimeErrors", List.copyOf(errors));
            report.put("passed", failure == null && errors.isEmpty() && failedAssertions.isEmpty());
            report.put("restored", resourcesRestored && disconnectComplete && minecraft.level == null && ConfigManager.activeProfile().equals(previousProfile));
            if (failure != null) report.put("error", failure.toString());
            Files.writeString(output.resolve("play.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            complete = true;
            if (keyCapture != null) keyCapture.unregister();
            if (chatCapture != null) chatCapture.unregister();
            removeErrorCapture();
            if (failure != null) completion.completeExceptionally(failure);
            else completion.complete(report);
        } catch (Throwable failure) { complete = true; if (keyCapture != null) keyCapture.unregister(); if (chatCapture != null) chatCapture.unregister(); removeErrorCapture(); completion.completeExceptionally(failure); }
    }

    private void progress() throws Exception {
        report.put("phase", phase);
        report.put("elapsedMs", (System.nanoTime() - started) / 1_000_000L);
        report.put("screenshots", screenshots);
        Files.writeString(output.resolve("play-progress.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private void installErrorCapture() {
        var context = (LoggerContext) LogManager.getContext(false);
        errorCapture = new AbstractAppender("ArcadiumPlayQa", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) {
                if (!event.getLevel().isMoreSpecificThan(Level.ERROR)) return;
                synchronized (errors) {
                    if (errors.size() >= 100) return;
                    String text = event.getLoggerName() + ": " + event.getMessage().getFormattedMessage();
                    if (event.getThrown() != null) text += " [" + event.getThrown() + "]";
                    errors.add(text.substring(0, Math.min(text.length(), 1500)));
                }
            }
        };
        errorCapture.start();
        context.getConfiguration().getRootLogger().addAppender(errorCapture, Level.ERROR, null);
        context.updateLoggers();
    }

    private void removeErrorCapture() {
        if (errorCapture == null) return;
        var context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getRootLogger().removeAppender(errorCapture.getName());
        context.updateLoggers();
        errorCapture.stop();
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static Object composeValue(Object owner, String name) throws Exception {
        return ((androidx.compose.runtime.State<?>) field(owner, name + "$delegate")).getValue();
    }
    @SuppressWarnings("unchecked")
    private static void setComposeValue(Object owner, String name, Object value) throws Exception {
        ((androidx.compose.runtime.MutableState<Object>) field(owner, name + "$delegate")).setValue(value);
    }
    private static String hudText(Hud hud) {
        try {
            Class<?> type = hud.getClass();
            while (type != null) {
                try {
                    var text = type.getDeclaredMethod("getText");
                    text.setAccessible(true);
                    return (String) text.invoke(hud);
                } catch (NoSuchMethodException next) { type = type.getSuperclass(); }
            }
            throw new IllegalStateException("HUD has no text provider");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static boolean close(float first, float second) { return Math.abs(first - second) < .01f; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
