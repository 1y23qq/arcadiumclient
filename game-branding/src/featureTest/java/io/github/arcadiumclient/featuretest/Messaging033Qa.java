package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.click;
import static io.github.arcadiumclient.featuretest.Visual033Qa.field;
import static io.github.arcadiumclient.featuretest.Visual033Qa.findField;
import static io.github.arcadiumclient.featuretest.Visual033Qa.hasText;
import static io.github.arcadiumclient.featuretest.Visual033Qa.nodes;
import static io.github.arcadiumclient.featuretest.Visual033Qa.object;
import static io.github.arcadiumclient.featuretest.Visual033Qa.properties;
import static io.github.arcadiumclient.featuretest.Visual033Qa.rectangle;
import static io.github.arcadiumclient.featuretest.Visual033Qa.semanticsRoot;
import static io.github.arcadiumclient.featuretest.Visual033Qa.stat;
import static io.github.arcadiumclient.featuretest.Visual033Qa.textNode;

import io.github.arcadiumclient.featuretest.mixin.MessagingChatProbeAccess;
import io.github.arcadiumclient.featuretest.mixin.AutoTextInputProbeAccess;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.Display;

public final class Messaging033Qa {
    private static final String FEATURES = "io.github.arcadiumclient.runtime.client.features.";
    private static final String KEYS = "org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager";
    private static final String BEDWARS = "io.github.arcadiumclient.runtime.client.bedwars.";
    private static final String FIRST_TEXT = "/play bedwars_eight_one", SECOND_TEXT = "ARC_AT033_SECOND", BLOCKED = "ARC_FILTER033_BLOCKED";
    private static final AtomicInteger COMMAND_PACKETS = new AtomicInteger();
    private static final List<String> TABS = List.of("Overlay", "AutoQueue", "Nick Bot", "Denick", "Style");
    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();
    private static final Map<Method, Object> CONFIG = new LinkedHashMap<>();
    private static final Map<Field, Object> OVERLAY = new LinkedHashMap<>();
    private static final Map<Path, byte[]> FILES = new LinkedHashMap<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static final BufferedImage[] PREFIX = new BufferedImage[4];
    private static Object auto, store, overlay, shell, originalLatest, originalPending, ui, first, second, chatTweaks;
    private static Field compactChat;
    private static Field historyLimit;
    private static boolean savedCompactChat;
    private static int savedHistoryLimit;
    private static int historyBeforeBlocked;
    private static long firstHistoryBaseline, secondHistoryBaseline;
    private static List<?> originalEntries;
    private static List<ChatMessage> originalRaw, originalVisible;
    private static List<String> originalRecent;
    private static PlayerEntity.ChatVisibility visibility;
    private static float opacity;
    private static String savedSearch;
    private static Screen parent;
    private static Path autoFile, output;
    private static int stage, ticks, firstKey, secondKey, prefixSample, bindingPhase;
    private static Object recordingOriginal;
    private static boolean begun, done, renderPending;
    private static Throwable renderFailure;

    private Messaging033Qa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if ((!Boolean.getBoolean("arcadium.release033Qa") && !Boolean.getBoolean("arcadium.autoText034Qa")) || done) return true;
        guard(mc);
        if (!begun) begin(mc, checks);
        if (renderFailure != null) throw new IllegalStateException("The messaging prefix render probe failed", renderFailure);
        require(++ticks < 180, "Messaging QA timed out at stage " + stage);
        switch (stage) {
            case 0 -> {
                if (ticks < 10) return false;
                ui = mc.screen;
                require(ui != null && ui.getClass().getSimpleName().equals("ArcadiumAutoTextScreen"), "AutoText's Manage Messages action did not open its real screen");
                require(hasText(ui, "AutoText") && hasText(ui, "Add message") && hasText(ui, "Done"), "AutoText management controls are missing");
                if (!Boolean.TRUE.equals(stat(FEATURES + "AutoTextConfig", "getEnabled"))) click(ui, "Disabled");
                click(ui, "Add message");
            }
            case 1 -> {
                if (ticks < 6) return false;
                require(entries().size() == 1, "The first AutoText UI add did not create an entry");
                first = entries().get(0);
                if (bindingPhase == 0) {
                    editText(ui, (String) call(first, "getName"), "M033 First");
                    editText(ui, "", FIRST_TEXT);
                }
                if (!bindThroughUi(first, firstKey)) return false;
                click(ui, "Add message");
            }
            case 2 -> {
                if (ticks < 6) return false;
                require(entries().size() == 2, "AutoText did not support multiple messages");
                second = entries().get(1);
                if (bindingPhase == 0) {
                    editText(ui, (String) call(second, "getName"), "M033 Second");
                    editText(ui, "", SECOND_TEXT);
                }
                if (!bindThroughUi(second, secondKey)) return false;
                clickNth(ui, "On", 1);
            }
            case 3 -> {
                if (ticks < 6) return false;
                require(Boolean.FALSE.equals(call(entries().get(1), "getEnabled")), "The per-message toggle did not disable the second entry");
                click(ui, "Off");
                call(auto, "save");
            }
            case 4 -> {
                if (ticks < 10 || !savedEntriesReady()) return false;
                RESULTS.put("autoTextUiBounds", Map.of("Add message", rectangle(textNode(ui, "Add message", false), mc, "Add message"),
                        "Done", rectangle(textNode(ui, "Done", false), mc, "Done")));
                capture(mc, "messaging033-autotext-manager.png");
                RESULTS.put("multiEntrySaveRead", true);
                press(firstKey, true);
            }
            case 5 -> {
                if (ticks < 8) return false;
                require(history(FIRST_TEXT) == 0, "An AutoText shortcut sent a message while editing AutoText");
                press(firstKey, false);
                click(ui, "Done");
                RESULTS.put("managementTypingProtected", true);
            }
            case 6 -> {
                if (ticks < 6 || mc.screen != null) return false;
                press(firstKey, true);
            }
            case 7 -> {
                if (ticks < 8 || history(FIRST_TEXT) < 1) return false;
                require(history(FIRST_TEXT) == 1, "The native key edge sent more than one AutoText message");
                press(firstKey, true);
            }
            case 8 -> {
                if (ticks < 8) return false;
                require(history(FIRST_TEXT) == 1, "Holding or repeating a shortcut resent AutoText after its cooldown");
                press(firstKey, false);
            }
            case 9 -> {
                if (ticks < 6) return false;
                press(firstKey, true);
                press(firstKey, false);
            }
            case 10 -> {
                if (ticks >= 12 && history(FIRST_TEXT) < 2) throw new IllegalStateException("A same-tick key tap was lost after binding through the AutoText UI");
                if (ticks < 8 || history(FIRST_TEXT) < 2) return false;
                require(history(FIRST_TEXT) == 2, "A fresh shortcut edge did not send exactly once");
                press(firstKey, false);
                mc.openScreen(new ChatScreen("Typing protection fixture"));
                press(secondKey, true);
                RESULTS.put("nativeShortcutEdges", 2);
                RESULTS.put("autoTextVCommandPackets", Map.of("key", "V", "scancode", firstKey, "command", FIRST_TEXT, "receivedByLocalServer", COMMAND_PACKETS.get()));
                RESULTS.put("sameTickShortcutTap", true);
                RESULTS.put("heldShortcutDoesNotRepeat", true);
            }
            case 11 -> {
                if (ticks < 8) return false;
                require(history(SECOND_TEXT) == 0, "AutoText sent while the native chat input was open");
                mc.openScreen(null);
            }
            case 12 -> {
                if (ticks < 8) return false;
                require(history(SECOND_TEXT) == 0, "A shortcut held through chat closing sent unexpectedly");
                press(secondKey, false);
            }
            case 13 -> {
                if (ticks < 6) return false;
                press(secondKey, true);
            }
            case 14 -> {
                if (ticks < 8 || history(SECOND_TEXT) < 1) return false;
                require(history(SECOND_TEXT) == 1, "The second entry did not send on a fresh post-typing key edge");
                press(secondKey, false);
                stat(FEATURES + "AutoTextConfig", "setEnabled", false);
                RESULTS.put("chatTypingAndHeldTransitionProtected", true);
            }
            case 15 -> {
                if (ticks < 6) return false;
                press(firstKey, true);
            }
            case 16 -> {
                if (ticks < 8) return false;
                require(history(FIRST_TEXT) == 2, "A disabled AutoText module sent a shortcut");
                press(firstKey, false);
                stat(FEATURES + "AutoTextConfig", "setEnabled", true);
                RESULTS.put("autoTextMasterOffProtected", true);
                chat().arcadium$rawMessages().removeIf(message -> message.getText().getString().contains("ARC_FILTER033_"));
                chat().arcadium$visibleMessages().removeIf(message -> message.getText().getString().contains("ARC_FILTER033_"));
                stat(FEATURES + "ChatFiltersConfig", "setWords", List.of(BLOCKED.toLowerCase(java.util.Locale.ROOT)));
                stat(FEATURES + "ChatFiltersConfig", "setCaseSensitive", false);
                stat(FEATURES + "ChatFiltersConfig", "setWholeWords", false);
                stat(FEATURES + "ChatFiltersConfig", "setEnabled", true);
                for (int line = 0; line < 160; line++) mc.gui.getChat().addMessage(new LiteralText("ARC_FILTER033_HISTORY_" + line));
                historyBeforeBlocked = chat().arcadium$rawMessages().size();
                require(historyBeforeBlocked > 150 && rawCount("ARC_FILTER033_HISTORY_0") == 1, "The expanded native chat history fixture did not retain its oldest sentinel");
                mc.gui.getChat().addMessage(new LiteralText("ARC_FILTER033_CLEAN"));
                mc.gui.getChat().addMessage(new LiteralText(BLOCKED + " normal"));
                mc.gui.getChat().addMessage(new LiteralText("ARC_FILTER033_OLD_STATUS"), 330331);
                mc.gui.getChat().addMessage(new LiteralText(BLOCKED + " updated"), 330331);
            }
            case 17 -> {
                if (ticks < 6) return false;
                require(rawCount(BLOCKED) == 2 && visibleCount(BLOCKED) == 0, "Normal or id-updated blocked messages reached visible chat");
                require(rawCount("ARC_FILTER033_OLD_STATUS") == 0 && visibleCount("ARC_FILTER033_OLD_STATUS") == 0,
                        "A filtered id-update left its old visible message behind");
                require(visibleCount("ARC_FILTER033_CLEAN") == 1, "The filter hid unrelated chat");
                int afterBlocked = chat().arcadium$rawMessages().size();
                require(afterBlocked > 150 && rawCount("ARC_FILTER033_HISTORY_0") == 1, "A blocked message shrank expanded raw chat history: before=" + historyBeforeBlocked + ", after=" + afterBlocked);
                RESULTS.put("expandedRawHistoryPreserved", Map.of("beforeBlocked", historyBeforeBlocked, "afterBlocked", afterBlocked, "oldestSentinelRetained", true));
                mc.openScreen(new ChatScreen());
                capture(mc, "messaging033-chat-filtered.png");
                stat(FEATURES + "ChatFiltersConfig", "setEnabled", false);
                stat(FEATURES + "ChatFiltersConfig", "hide", (Object) null);
            }
            case 18 -> {
                if (ticks < 6) return false;
                require(rawCount(BLOCKED) == 2 && visibleCount(BLOCKED) >= 2, "Disabling filters did not restore preserved blocked history");
                require(rawById(330331) == 1, "Filter refresh duplicated the id-updated message in raw history");
                capture(mc, "messaging033-chat-restored.png");
                stat(FEATURES + "ChatFiltersConfig", "setEnabled", true);
                stat(FEATURES + "ChatFiltersConfig", "setWords", List.of());
                stat(FEATURES + "ChatFiltersConfig", "hide", (Object) null);
                RESULTS.put("normalAndIdUpdatedFiltering", true);
                RESULTS.put("hiddenHistoryRestoredOnToggle", true);
            }
            case 19 -> {
                if (ticks < 6) return false;
                require(visibleCount(BLOCKED) >= 2 && rawById(330331) == 1, "Changing filter words did not rebuild visible history safely");
                call(shell, "setSearchQuery", "");
                Object route = Class.forName("org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute")
                        .getConstructor(String.class, String.class).newInstance(field(overlay, "id"), "Overlay");
                stat("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", route);
                RESULTS.put("filterRuleRefreshPreservedIds", true);
            }
            case 20 -> {
                if (ticks < 12) return false;
                ui = mc.screen;
                require(ui != null && ui.getClass().getSimpleName().contains("OneConfigUIScreen"), "The Bedwars config did not open in Arcadium Options");
                Map<String, Object> tabs = new LinkedHashMap<>();
                for (String tab : TABS) tabs.put(tab, rectangle(textNode(ui, tab, true), mc, "Bedwars " + tab));
                RESULTS.put("bedwarsCategoryTabs", tabs);
                click(ui, "AutoQueue");
            }
            case 21 -> {
                if (ticks < 6) return false;
                require(hasText(ui, "Cancel Pending Queue"), "The AutoQueue category did not select its own settings");
                click(ui, "Nick Bot");
            }
            case 22 -> {
                if (ticks < 6) return false;
                require(hasText(ui, "Nick Interval"), "The Nick Bot category did not select its own settings");
                click(ui, "Denick");
            }
            case 23 -> {
                if (ticks < 6) return false;
                require(hasText(ui, "Skin Lookup") && hasText(ui, "UUID Lookup"), "The Denick category did not select its own settings");
                click(ui, "Style");
            }
            case 24 -> {
                if (ticks < 6) return false;
                require(hasText(ui, "Animated Arcadium Prefix") && hasText(ui, "Gradient Start") && hasText(ui, "Gradient End"),
                        "The Style category is missing the prefix controls");
                capture(mc, "messaging033-bedwars-style.png");
                RESULTS.put("bedwarsTabsSelectIndependentSettings", true);
                mc.openScreen(null);
                findField(overlay.getClass(), "animatedPrefix").setBoolean(overlay, true);
                prefixSample = 0; renderPending = true;
            }
            case 25 -> {
                if (ticks < 14 || PREFIX[0] == null) return false;
                prefixSample = 1; renderPending = true;
            }
            case 26 -> {
                if (PREFIX[1] == null) return false;
                int prefixEnd = 8 + mc.textRenderer.getWidth("Arcadium");
                int animation = differences(PREFIX[0], PREFIX[1], 7, prefixEnd + 1);
                require(animation > 0, "The actual Arcadium prefix pixels did not animate");
                require(differences(PREFIX[0], PREFIX[1], prefixEnd + 2, 420) == 0, "The prefix animation also changed the delimiter or message body");
                RESULTS.put("animatedPrefixPixelDifferences", animation);
                RESULTS.put("unchangedMessageBodyPixels", true);
                findField(overlay.getClass(), "animatedPrefix").setBoolean(overlay, false);
                prefixSample = 2; renderPending = true;
            }
            case 27 -> {
                if (ticks < 14 || PREFIX[2] == null) return false;
                prefixSample = 3; renderPending = true;
            }
            case 28 -> {
                if (PREFIX[3] == null) return false;
                require(differences(PREFIX[2], PREFIX[3], 0, 420) == 0, "The disabled prefix animation still changed rendered pixels");
                int start = (Integer) field(overlay, "prefixStart"), end = (Integer) field(overlay, "prefixEnd");
                for (int letter = 0; letter < 8; letter++) require(((Number) stat(BEDWARS + "ArcadiumPrefix", "color", letter, System.nanoTime(), false, start, end)).intValue() == (start | 0xFF000000),
                        "Disabling the gradient did not select one static prefix color");
                RESULTS.put("prefixAnimationToggleRendered", true);
                for (Object entry : new ArrayList<>(entries())) call(auto, "remove", call(entry, "getId"));
                call(auto, "save");
                press(firstKey, false); press(secondKey, false);
                stat(KEYS, "clearState");
            }
            case 29 -> {
                if (ticks < 8) return false;
                Future<?> pending = (Future<?>) field(store, "pending");
                if (pending != null && !pending.isDone()) return false;
                require(entries().isEmpty(), "AutoText fixture entries were not removed");
                restore(mc);
                RESULTS.put("fixtureFilesAndChatRestored", true);
                RESULTS.put("completed", true);
                done = true;
                return true;
            }
            default -> throw new IllegalStateException("Unknown messaging QA stage");
        }
        stage++; ticks = 0; return false;
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        begun = true;
        checks.put("messaging033", RESULTS); RESULTS.put("screenshots", CAPTURES);
        parent = mc.screen; visibility = mc.options.chatVisibility; opacity = mc.options.chatOpacity;
        mc.options.chatVisibility = PlayerEntity.ChatVisibility.FULL; mc.options.chatOpacity = 1f;
        originalRaw = new ArrayList<>(chat().arcadium$rawMessages()); originalVisible = new ArrayList<>(chat().arcadium$visibleMessages());
        originalRecent = new ArrayList<>(mc.gui.getChat().getRecentMessages());
        Path game = mc.gameDir.toPath().toRealPath();
        require(Path.of(System.getProperty("arcadium.forgeQa.gameDir")).toRealPath().equals(game), "Messaging QA requires the owned fixture directory");
        autoFile = FabricLoader.getInstance().getConfigDir().resolve("arcadium/autotext.json").toAbsolutePath().normalize();
        backup(autoFile, game);
        auto = object(FEATURES + "AutoText"); store = field(auto, "store");
        require(store != null && Boolean.TRUE.equals(field(auto, "writable")), "AutoText storage is unavailable");
        originalEntries = new ArrayList<>(entries());
        require(originalEntries.isEmpty(), "Messaging QA requires a fresh AutoText fixture");
        originalLatest = field(store, "latest"); originalPending = field(store, "pending");
        Path folder = ((Path) call(stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active"), "getFolder")).toAbsolutePath().normalize();
        snapshot(FEATURES + "AutoTextConfig", folder, game);
        snapshot(FEATURES + "ChatFiltersConfig", folder, game);
        stat(FEATURES + "ChatFiltersConfig", "setEnabled", false);
        if (FabricLoader.getInstance().isModLoaded("chattweaks")) {
            chatTweaks = Class.forName("org.polyfrost.chattweaks.ChatTweaks").getField("config").get(null);
            compactChat = findField(chatTweaks.getClass(), "compactChat");
            savedCompactChat = compactChat.getBoolean(chatTweaks);
            historyLimit = findField(chatTweaks.getClass(), "increaseChatHistoryLimit");
            savedHistoryLimit = historyLimit.getInt(chatTweaks);
            backup(folder.resolve((String) field(chatTweaks, "id")), game);
            compactChat.setBoolean(chatTweaks, false);
            historyLimit.setInt(chatTweaks, Math.max(256, savedHistoryLimit));
            RESULTS.put("duplicateCollapsingDisabledInFixture", true);
        }
        firstHistoryBaseline = rawCount(FIRST_TEXT); secondHistoryBaseline = rawCount(SECOND_TEXT);
        overlay = object(BEDWARS + "BedwarsOverlayConfig");
        for (Field field : overlay.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
            OVERLAY.put(field, field.get(overlay));
        }
        backup(folder.resolve((String) field(overlay, "id")), game);
        require(Boolean.TRUE.equals(field(overlay, "enabled")) && Boolean.TRUE.equals(field(overlay, "keyless")) && Boolean.FALSE.equals(field(overlay, "useHypixelApiKey")),
                "Bedwars keyless defaults or explicit-key opt-in are incorrect");
        require(Boolean.FALSE.equals(field(overlay, "autoQueue")) && Boolean.FALSE.equals(field(overlay, "nickBot")) && Boolean.FALSE.equals(field(overlay, "denick")) && Boolean.FALSE.equals(field(overlay, "seraphAlerts")),
                "An optional Bedwars automation or external lookup was enabled by default");
        require(Boolean.FALSE.equals(stat(BEDWARS + "BedwarsOverlay", "isInBedwars")), "Messaging QA must not enter a live Hypixel context");
        RESULTS.put("bedwarsKeylessOnExplicitKeyOff", true);
        RESULTS.put("optionalBedwarsAutomationsOff", true);
        shell = object("org.polyfrost.oneconfig.internal.ui.shell.ShellState"); savedSearch = (String) call(shell, "getSearchQuery");
        output = game.resolve("forge-qa"); Files.createDirectories(output);
        firstKey = 25;
        secondKey = freeKey(List.of(10, 11, 13, 14, 15).stream().filter(key -> key != firstKey).toList());
        require(firstKey != secondKey, "The messaging fixture shortcuts conflict");
        Object config = object(FEATURES + "AutoTextConfig");
        Object edit = call(call(config, "getTree"), "get", "edit");
        require(edit != null, "The AutoText config has no Manage Messages action");
        ((Runnable) call(edit, "getMetadata", "runnable")).run();
    }

    private static void guard(Minecraft mc) {
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Messaging QA requires its isolated offline account");
        require(mc.world != null && mc.player != null && mc.isSingleplayer() && mc.getServer() != null && !mc.getServer().isPublished()
                        && mc.getServer().getPlayerManager().getAll().size() == 1,
                "AutoText key QA may send only to the unpublished single-player fixture");
    }

    private static int freeKey(List<Integer> candidates) throws Exception {
        Collection<?> binds = (Collection<?>) field(object(KEYS), "binds");
        for (int candidate : candidates) {
            boolean used = false;
            for (Object bind : binds) {
                int[] keys = (int[]) call(bind, "getKeyCodes");
                if (keys != null) for (int key : keys) if (key == candidate) used = true;
            }
            if (!used) return candidate;
        }
        throw new IllegalStateException("No isolated function-key shortcut is available");
    }
    private static boolean bindThroughUi(Object entry, int key) throws Exception {
        Object property = call(auto, "binding", call(entry, "getId"));
        switch (bindingPhase) {
            case 0 -> {
                recordingOriginal = call(property, "get");
                click(ui, "None");
                bindingPhase = 1;
            }
            case 1 -> {
                if (ticks < 4) return false;
                require(Boolean.TRUE.equals(stat("org.polyfrost.oneconfig.internal.ui.keybind.KeybindRecordingBus", "isRecording")), "The actual AutoText shortcut control did not start recording");
                require(key >= 4 && key <= 29, "The AutoText UI fixture requires a physical letter key");
                if (!recorderReady(key)) return false;
                AutoTextInputProbeAccess input = (AutoTextInputProbeAccess) ui;
                int character = 'a' + key - 4;
                boolean accepted = input.arcadium$autoTextKeyDown(key, character, 0);
                RESULTS.put("autoTextKeyDownAccepted" + key, accepted);
                require(accepted, "The AutoText recorder did not receive its real key-down event: " + RESULTS.get("autoTextRecorder" + key));
                input.arcadium$autoTextCharacter((char) character, character, 0);
                bindingPhase = 2;
            }
            case 2 -> {
                if (ticks < 4) return false;
                String label = (String) stat("org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind", "keyName", key);
                require(hasText(ui, label), "The AutoText recorder did not display its captured physical key: " + label);
                ((AutoTextInputProbeAccess) ui).arcadium$autoTextKeyUp(key, 'a' + key - 4, 0);
                bindingPhase = 3;
            }
            case 3 -> {
                if (ticks < 4) return false;
                Object bind = call(property, "get");
                int[] codes = (int[]) call(bind, "getKeyCodes");
                boolean registered = Boolean.TRUE.equals(stat(KEYS, "isRegistered", bind));
                boolean oldRegistered = Boolean.TRUE.equals(stat(KEYS, "isRegistered", recordingOriginal));
                boolean recording = Boolean.TRUE.equals(stat("org.polyfrost.oneconfig.internal.ui.keybind.KeybindRecordingBus", "isRecording"));
                RESULTS.put("autoTextUiBind" + key, Map.of("keys", codes == null ? List.of() : java.util.Arrays.stream(codes).boxed().toList(),
                        "registered", registered, "oldRegistered", oldRegistered, "sameInstance", bind == recordingOriginal,
                        "actionPreserved", call(bind, "getAction") == call(recordingOriginal, "getAction"), "recordingAfterRelease", recording));
                require(codes != null && codes.length == 1 && codes[0] == key, "Recording through the AutoText UI stored the wrong key codes");
                require(registered && (bind == recordingOriginal || !oldRegistered), "Recording through the AutoText UI left a stale registered shortcut");
                require(!recording, "AutoText's key recorder remained active after releasing its shortcut");
                bindingPhase = 0;
                return true;
            }
            default -> throw new IllegalStateException("Unknown AutoText UI binding phase");
        }
        ticks = 0;
        return false;
    }
    private static boolean recorderReady(int key) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        guard(mc);
        require(mc.screen == ui, "The AutoText recorder's screen changed before input");
        Object node = textNode(ui, "Press keys...", true);
        Map<String, Object> props = properties(node);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("windowActive", Display.isActive());
        state.put("recorderFocused", Boolean.TRUE.equals(props.get("Focused")));
        state.put("focusActionAvailable", props.containsKey("RequestFocus"));
        state.put("sceneBusy", field(ui, "sceneBusy"));
        state.put("scenePoisoned", field(ui, "scenePoisoned"));
        state.put("contentSet", field(ui, "contentSet"));
        state.put("ticksWaiting", ticks);
        RESULTS.put("autoTextRecorder" + key, state);
        boolean ready = Boolean.TRUE.equals(state.get("windowActive")) && Boolean.TRUE.equals(state.get("recorderFocused"))
                && Boolean.FALSE.equals(state.get("sceneBusy")) && Boolean.FALSE.equals(state.get("scenePoisoned"));
        if (ready) return true;
        require(ticks < 40, "The AutoText recorder did not acquire native window and control focus: " + state);
        if (ticks % 4 == 0) {
            long handle = ((Number) call(stat("org.polyfrost.oneconfig.api.platform.v1.Platform", "compatibility"), "windowHandle")).longValue();
            require(handle != 0, "The isolated AutoText fixture has no SDL window");
            state.put("windowRaised", stat("org.lwjgl.sdl.SDLVideo", "SDL_RaiseWindow", handle));
            Object request = props.get("RequestFocus");
            if (request != null) state.put("focusRequested", call(call(request, "getAction"), "invoke"));
        }
        return false;
    }
    private static boolean savedEntriesReady() throws Exception {
        if (!Files.isRegularFile(autoFile)) return false;
        List<?> read = (List<?>) call(object(FEATURES + "AutoTextData"), "read", autoFile);
        if (read.size() != 2) return false;
        return "M033 First".equals(call(read.get(0), "getName")) && FIRST_TEXT.equals(call(read.get(0), "getText"))
                && "M033 Second".equals(call(read.get(1), "getName")) && SECOND_TEXT.equals(call(read.get(1), "getText"))
                && ((List<?>) call(read.get(0), "getKeys")).equals(List.of(firstKey)) && ((List<?>) call(read.get(1), "getKeys")).equals(List.of(secondKey));
    }
    private static List<?> entries() throws Exception { return (List<?>) call(auto, "getEntries"); }
    private static void press(int key, boolean down) throws Exception {
        Method method = ForgeQa.class.getDeclaredMethod("pushKey", int.class, boolean.class); method.setAccessible(true); method.invoke(null, key, down);
    }
    private static MessagingChatProbeAccess chat() { return (MessagingChatProbeAccess) Minecraft.getInstance().gui.getChat(); }
    private static long history(String marker) { return marker.equals(FIRST_TEXT) ? COMMAND_PACKETS.get() : rawCount(marker) - secondHistoryBaseline; }

    public static boolean interceptCommand(MinecraftServer server, ServerPlayerEntity player, String message) {
        if (!begun || done || !FIRST_TEXT.equals(message) || !server.isOnSameThread()) return false;
        Minecraft mc = Minecraft.getInstance();
        guard(mc);
        require(server == mc.getServer() && "ArcadiumTest".equals(player.getName()), "The AutoText command probe must use its unpublished local server");
        COMMAND_PACKETS.incrementAndGet();
        System.out.println("AutoText UI command received by local server: " + message);
        return true;
    }
    private static long rawCount(String marker) { return chat().arcadium$rawMessages().stream().filter(message -> message.getText().getString().contains(marker)).count(); }
    private static long visibleCount(String marker) { return chat().arcadium$visibleMessages().stream().filter(message -> message.getText().getString().contains(marker)).count(); }
    private static long rawById(int id) { return chat().arcadium$rawMessages().stream().filter(message -> message.getId() == id).count(); }

    public static void afterFrame() {
        if (!begun || done || !renderPending || renderFailure != null) return;
        renderPending = false;
        try {
            Minecraft mc = Minecraft.getInstance();
            PREFIX[prefixSample] = prefixImage(mc);
            String name = "messaging033-prefix-" + prefixSample + ".png";
            ImageIO.write(PREFIX[prefixSample], "PNG", output.resolve(name).toFile()); CAPTURES.add("forge-qa/" + name);
        } catch (Throwable failure) { renderFailure = failure; }
    }

    private static BufferedImage prefixImage(Minecraft mc) throws Exception {
        Object state = Class.forName("org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLState").getConstructor(int.class).newInstance(330);
        call(state, "capture");
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE), fbo = GL30.glGenFramebuffers(), texture = GL11.glGenTextures();
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity(); GL11.glOrtho(0, 420, 64, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            GL20.glUseProgram(0); GlStateManager.bindTexture(texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 420, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo); GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "The prefix evidence framebuffer is incomplete");
            GL11.glViewport(0, 0, 420, 64); GlStateManager.disableDepthTest(); GlStateManager.disableLighting(); GlStateManager.disableCull();
            GlStateManager.enableTexture(); GlStateManager.enableBlend(); GlStateManager.blendFuncSeparate(770, 771, 1, 0); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(16f / 255, 16f / 255, 16f / 255, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            String marked = (String) stat(BEDWARS + "ArcadiumPrefix", "message", "MSG033_BODY");
            require(marked.equals("\u2063Arcadium\u2064 §7> §fMSG033_BODY"), "The client prefix format is incorrect");
            int markedWidth = mc.textRenderer.getWidth(marked), plainWidth = mc.textRenderer.getWidth("Arcadium §7> §fMSG033_BODY");
            int startWidth = mc.textRenderer.getWidth('\u2063'), endWidth = mc.textRenderer.getWidth('\u2064');
            Map<String, Object> widths = Map.of("marked", markedWidth, "plain", plainWidth, "startMarker", startWidth, "endMarker", endWidth, "unicode", mc.textRenderer.getUnicode());
            RESULTS.put("prefixWidthsSample" + prefixSample, widths);
            require(markedWidth == plainWidth && startWidth == 0 && endWidth == 0, "Prefix markers changed the real text width: " + widths);
            mc.textRenderer.draw(marked, 8f, 20f, -1, false);
            ByteBuffer pixels = BufferUtils.createByteBuffer(420 * 64 * 4); GL11.glReadPixels(0, 0, 420, 64, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(420, 64, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 420; x++) {
                int at = ((63 - y) * 420 + x) * 4;
                image.setRGB(x, y, (pixels.get(at + 3) & 255) << 24 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255);
            }
            require(ink(image) > 50, "The actual prefix and message glyphs did not render");
            return image;
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glMatrixMode(mode);
            call(state, "restore"); stat("org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLStateKt", "resyncTextureBindCache");
            GL30.glDeleteFramebuffers(fbo); GlStateManager.deleteTextures(texture);
        }
    }
    private static int ink(BufferedImage image) {
        int count = 0; for (int y = 0; y < 64; y++) for (int x = 0; x < 420; x++) if ((image.getRGB(x, y) & 0xFFFFFF) != 0x101010) count++; return count;
    }
    private static int differences(BufferedImage first, BufferedImage second, int left, int right) {
        int count = 0; for (int y = 0; y < 64; y++) for (int x = Math.max(0, left); x < Math.min(420, right); x++) if (first.getRGB(x, y) != second.getRGB(x, y)) count++; return count;
    }

    private static void snapshot(String type, Path folder, Path game) throws Exception {
        Class<?> config = Class.forName(type);
        for (Method getter : config.getDeclaredMethods()) {
            if (!Modifier.isStatic(getter.getModifiers()) || getter.getParameterCount() != 0 || !getter.getName().startsWith("get")) continue;
            try { CONFIG.put(config.getMethod("set" + getter.getName().substring(3), getter.getReturnType()), getter.invoke(null)); }
            catch (NoSuchMethodException ignored) { }
        }
        backup(folder.resolve((String) field(object(type), "id")), game);
    }
    private static void backup(Path file, Path game) throws Exception {
        file = file.toAbsolutePath().normalize(); require(file.startsWith(game), "Messaging QA would modify a file outside its fixture");
        Path existing = file; while (!Files.exists(existing, java.nio.file.LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        require(existing.toRealPath().startsWith(game), "Messaging QA encountered a link outside its fixture");
        FILES.putIfAbsent(file, Files.isRegularFile(file) ? Files.readAllBytes(file) : null);
    }
    private static void restore(Minecraft mc) throws Exception {
        mc.openScreen(null);
        findField(store.getClass(), "latest").set(store, originalLatest); findField(store.getClass(), "pending").set(store, originalPending);
        for (var entry : CONFIG.entrySet()) entry.getKey().invoke(null, entry.getValue());
        for (var entry : OVERLAY.entrySet()) entry.getKey().set(overlay, entry.getValue());
        if (compactChat != null) compactChat.setBoolean(chatTweaks, savedCompactChat);
        if (historyLimit != null) historyLimit.setInt(chatTweaks, savedHistoryLimit);
        stat(FEATURES + "ChatFiltersConfig", "hide", (Object) null);
        chat().arcadium$rawMessages().clear(); chat().arcadium$rawMessages().addAll(originalRaw);
        chat().arcadium$visibleMessages().clear(); chat().arcadium$visibleMessages().addAll(originalVisible);
        mc.gui.getChat().getRecentMessages().clear(); mc.gui.getChat().getRecentMessages().addAll(originalRecent);
        call(shell, "setSearchQuery", savedSearch);
        for (var entry : FILES.entrySet()) if (entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else Files.write(entry.getKey(), entry.getValue());
        mc.options.chatVisibility = visibility; mc.options.chatOpacity = opacity; mc.openScreen(parent);
    }
    private static void editText(Object screen, String expected, String next) throws Exception {
        for (Object node : nodes(semanticsRoot(screen))) {
            Map<String, Object> props = properties(node); Object action = props.get("SetText"), editable = props.get("EditableText");
            if (action == null || editable == null || !expected.equals(call(editable, "getText"))) continue;
            Object text = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class).newInstance(next, List.of(), List.of());
            require(Boolean.TRUE.equals(call(call(action, "getAction"), "invoke", text)), "The AutoText field rejected an edit"); return;
        }
        throw new IllegalStateException("The AutoText input was not found");
    }
    private static void clickNth(Object screen, String label, int index) throws Exception {
        for (Object node : nodes(semanticsRoot(screen))) {
            Map<String, Object> props = properties(node); if (!props.containsKey("OnClick") || !(props.get("Text") instanceof Collection<?> values)) continue;
            for (Object value : values) if (label.equals(call(value, "getText"))) {
                if (index-- == 0) { require(Boolean.TRUE.equals(call(call(props.get("OnClick"), "getAction"), "invoke")), "The AutoText control rejected a click"); return; }
            }
        }
        throw new IllegalStateException("The AutoText control was not found: " + label);
    }
    private static void capture(Minecraft mc, String file) { Screenshot.take(mc.gameDir, file, mc.width, mc.height, mc.getRenderTarget()); CAPTURES.add(file); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
