package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumServersScreen;
import io.github.arcadiumclient.runtime.mixin.client.access.ChatScreenAccessor;
import io.github.arcadiumclient.runtime.mixin.compat.ComposeTextInputAccess;
import io.github.arcadiumclient.runtime.qa.mixin.MouseInputAccess;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.screen.game.inventory.ChestScreen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.gui.screen.menu.multiplayer.AddServerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class Ime058Qa {
    public static InventorySlot hoveredSlot;
    public static final List<Integer> nativeKeys = new ArrayList<>();
    public static final List<Object> slotClicks = new ArrayList<>();
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static final List<ByteBuffer> textBuffers = new ArrayList<>();
    private static final String JAPANESE = "日本語入力 １２３";
    private static int ticks, stage, stageTicks, tabAttempts;
    private static boolean done;
    private static SimpleInventory chest;
    private static TextFieldWidget nativeField;
    private static ArcadiumServersScreen compose;

    public static boolean enabled() { return System.getProperty("arcadium.ime058.output") != null; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static Path output() { return Path.of(System.getProperty("arcadium.ime058.output")); }
    private static void next(int value) { stage = value; stageTicks = 0; }

    public static void tick() {
        if (done) return;
        var mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use an isolated offline account");
            require(++ticks < 1800, "IME QA timed out at stage " + stage);
            stageTicks++;
            OneConfigConfig.limitUnfocusedFps = false;
            mc.options.pauseOnUnfocus = false;
            if (stage == 0 && ticks > 60) {
                ClientPreferences.setSetupComplete(true);
                mc.openScreen(new TitleScreen());
                next(1);
            } else if (stage == 1 && stageTicks > 5) {
                state("mainMenu", false);
                var screen = new ArcadiumServersScreen(new TitleScreen());
                mc.openScreen(screen);
                var connect = ArcadiumServersScreen.class.getDeclaredMethod("connect", ServerListEntry.class);
                connect.setAccessible(true);
                connect.invoke(screen, new ServerListEntry("Local IME QA", System.getProperty("arcadium.ime058.server"), false));
                next(2);
            } else if (stage == 2 && mc.world != null && mc.player != null && mc.screen == null) {
                state("gameplay", false);
                mc.openScreen(new ChatScreen(""));
                next(3);
            } else if (stage == 3 && stageTicks > 5) {
                state("firstChat", true);
                Files.writeString(output().resolveSibling("ime-request.json"), "{\"ready\":true}");
                next(4);
            } else if (stage == 4 && Files.exists(output().resolveSibling("ime-mode.json"))) {
                result.put("osIme", new com.google.gson.JsonParser().parse(Files.readString(output().resolveSibling("ime-mode.json"))));
                pushText(JAPANESE);
                next(5);
            } else if (stage == 5 && stageTicks > 5) {
                require(chatText().equals(JAPANESE), "Native Japanese input changed: " + chatText());
                result.put("japaneseChat", chatText());
                screenshot("ime058-japanese-chat.png");
                mc.player.inventory.setItem(9, stack(1, 12));
                mc.player.inventory.setItem(0, stack(266, 3));
                mc.player.inventory.setItem(8, stack(265, 5));
                mc.openScreen(new SurvivalInventoryScreen(mc.player));
                var inventory = (InventoryMenuScreen) mc.screen;
                hoveredSlot = inventory.menu.getSlot(mc.player.inventory, 9);
                require(hoveredSlot != null, "Inventory fixture slot missing");
                next(6);
            } else if (stage == 6 && stageTicks > 5) {
                state("inventoryAfterJapaneseIme", false);
                pushKey(30, true);
                next(7);
            } else if (stage == 7 && stageTicks > 4) {
                pushKey(30, false);
                require(item(mc.player.inventory.getItem(0)) == 1 && item(mc.player.inventory.getItem(9)) == 266, "Inventory native number 1 did not swap stacks");
                pushKey(38, true);
                next(8);
            } else if (stage == 8 && stageTicks > 4) {
                pushKey(38, false);
                require(item(mc.player.inventory.getItem(8)) == 266 && item(mc.player.inventory.getItem(9)) == 265, "Inventory native number 9 did not swap stacks");
                result.put("inventoryNativeHotbar1And9", true);
                screenshot("ime058-inventory-hotbar.png");
                hoveredSlot = null;
                chest = new SimpleInventory("Bed Wars Item Shop QA", true, 27);
                chest.setItem(0, stack(35, 16));
                mc.player.inventory.setItem(9, stack(1, 12));
                mc.player.inventory.setItem(0, stack(266, 4));
                mc.player.inventory.setItem(8, stack(265, 7));
                mc.openScreen(new ChestScreen(mc.player.inventory, chest));
                hoveredSlot = ((InventoryMenuScreen) mc.screen).menu.getSlot(mc.player.inventory, 9);
                require(hoveredSlot != null, "Chest fixture slot missing");
                next(9);
            } else if (stage == 9 && stageTicks > 5) {
                state("bedwarsChest", false);
                result.put("chestFixture", Map.of("hoverIndex", hoveredSlot.index, "playerMenuMatches", mc.player.menu == ((InventoryMenuScreen) mc.screen).menu,
                    "cursor", String.valueOf(mc.player.inventory.getCursorItem()), "menu", mc.player.menu.getClass().getName()));
                pushKey(30, true);
                next(10);
            } else if (stage == 10 && stageTicks > 4) {
                pushKey(30, false);
                require(item(mc.player.inventory.getItem(0)) == 1 && item(mc.player.inventory.getItem(9)) == 266, "Chest native number 1 did not swap inventory stacks");
                pushKey(38, true);
                next(11);
            } else if (stage == 11 && stageTicks > 4) {
                pushKey(38, false);
                require(item(mc.player.inventory.getItem(8)) == 266 && item(mc.player.inventory.getItem(9)) == 265, "Chest native number 9 did not swap inventory stacks");
                require(item(chest.getItem(0)) == 35 && chest.getItem(0).size == 16, "Inventory hotbar shortcut modified the shop item");
                result.put("chestNativeHotbar1And9", true);
                screenshot("ime058-chest-hotbar.png");
                pushKey(20, true);
                next(12);
            } else if (stage == 12 && stageTicks > 4) {
                pushKey(20, false);
                require(mc.player.inventory.getItem(9).size == 6, "Native Q did not drop one hovered inventory item in the chest screen");
                result.put("chestNativeDropKey", true);
                hoveredSlot = null;
                pushKey(41, true);
                next(13);
            } else if (stage == 13 && stageTicks > 4) {
                pushKey(41, false);
                require(mc.screen == null, "Native Escape did not close the chest");
                state("afterNativeEscape", false);
                result.put("chestNativeEscape", true);
                mc.openScreen(new ChatScreen(""));
                next(14);
            } else if (stage == 14 && stageTicks > 5) {
                state("reopenedChat", true);
                pushText(JAPANESE);
                next(15);
            } else if (stage == 15 && stageTicks > 5) {
                require(chatText().equals(JAPANESE), "Japanese input failed after inventory and chest transitions");
                result.put("chatInventoryChestChatRoundTrip", true);
                mc.openScreen(new AddServerScreen(new TitleScreen(), new ServerListEntry("IME QA", "127.0.0.1", false)));
                require(mc.screen instanceof AddServerScreen, "Native text fixture was replaced");
                nativeField = focusedField(mc.screen);
                nativeField.setText("");
                next(16);
            } else if (stage == 16 && stageTicks > 5) {
                state("focusedNativeTextField", true);
                pushText(JAPANESE);
                next(17);
            } else if (stage == 17 && stageTicks > 5) {
                require(nativeField.getText().equals(JAPANESE), "Focused native field lost Japanese input: " + nativeField.getText());
                ((MouseInputAccess) mc.screen).release$press(4, 4, 0);
                ((MouseInputAccess) mc.screen).release$release(4, 4, 0);
                require(!nativeField.isFocused(), "Native field did not lose focus after an outside click");
                next(18);
            } else if (stage == 18 && stageTicks > 5) {
                state("unfocusedNativeTextField", false);
                ((MouseInputAccess) mc.screen).release$press(nativeField.x + 10, nativeField.y + 8, 0);
                ((MouseInputAccess) mc.screen).release$release(nativeField.x + 10, nativeField.y + 8, 0);
                require(nativeField.isFocused(), "Native field did not regain focus");
                next(19);
            } else if (stage == 19 && stageTicks > 5) {
                state("refocusedNativeTextField", true);
                result.put("nativeTextFieldFocusAndJapanese", true);
                compose = new ArcadiumServersScreen(new TitleScreen());
                mc.openScreen(compose);
                next(20);
            } else if (stage == 20 && stageTicks > 10) {
                state("composeWithoutTextFocus", false);
                pushKey(43, true);
                next(21);
            } else if (stage == 21 && stageTicks > 4) {
                pushKey(43, false);
                next(22);
            } else if (stage == 22 && stageTicks > 5) {
                require(mc.screen == compose, "Compose screen closed while focusing search");
                if (!((ComposeTextInputAccess) (Object) compose).arcadium$isEditingText()) {
                    require(++tabAttempts < 8, "Native Tab could not focus the Compose server search");
                    pushKey(43, true);
                    next(21);
                } else {
                    state("focusedComposeSearch", true);
                    pushText(JAPANESE);
                    next(23);
                }
            } else if (stage == 23 && stageTicks > 8) {
                var query = ArcadiumServersScreen.class.getDeclaredMethod("getQuery");
                query.setAccessible(true);
                require(query.invoke(compose).equals(JAPANESE), "Compose search lost native Japanese input: " + query.invoke(compose));
                state("composeSessionAfterSeveralTicks", true);
                screenshot("ime058-compose-japanese-search.png");
                result.put("composeSearchFocusAndJapanese", true);
                pushKey(43, true);
                next(24);
            } else if (stage == 24 && stageTicks > 4) {
                pushKey(43, false);
                next(25);
            } else if (stage == 25 && stageTicks > 5) {
                require(!((ComposeTextInputAccess) (Object) compose).arcadium$isEditingText(), "Compose search stayed focused after Tab");
                state("composeAfterTextBlur", false);
                result.put("nativeKeys", List.copyOf(nativeKeys));
                result.put("slotClicks", List.copyOf(slotClicks));
                require(nativeKeys.containsAll(List.of(2, 10, 16, 1)) && slotClicks.size() >= 5, "Native inventory route was not observed");
                finish(null);
            }
        } catch (Throwable error) { finish(error); }
    }

    public static void frame() { }

    private static String chatText() { return ((ChatScreenAccessor) Minecraft.getInstance().screen).arcadium$chatField().getText(); }
    private static ItemStack stack(int id, int count) { return new ItemStack(Item.byId(id), count); }
    private static int item(ItemStack stack) { return stack == null ? -1 : Item.getId(stack.getItem()); }
    private static TextFieldWidget focusedField(Screen screen) throws Exception {
        for (var field : screen.getClass().getDeclaredFields()) {
            if (!TextFieldWidget.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            var input = (TextFieldWidget) field.get(screen);
            if (input != null && input.isFocused()) return input;
        }
        throw new IllegalStateException("Native fixture has no focused text field");
    }

    private static long window() throws Exception {
        var display = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
        return (long) display.getMethod("getHandle").invoke(display.getMethod("instance").invoke(null));
    }

    private static int windowId() throws Exception { return (int) Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_GetWindowID", long.class).invoke(null, window()); }

    private static void state(String name, boolean expected) throws Exception {
        boolean actual = (boolean) Class.forName("org.lwjgl.sdl.SDLKeyboard").getMethod("SDL_TextInputActive", long.class).invoke(null, window());
        result.put(name, Map.of("sdlTextInputActive", actual, "screen", Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName()));
        require(actual == expected, name + " SDL text input was " + actual + ", expected " + expected);
    }

    private static void pushKey(int scancode, boolean down) throws Exception {
        var type = Class.forName("org.lwjgl.sdl.SDL_Event");
        var events = Class.forName("org.lwjgl.sdl.SDLEvents");
        Object event = type.getMethod("calloc").invoke(null);
        try {
            int kind = events.getField(down ? "SDL_EVENT_KEY_DOWN" : "SDL_EVENT_KEY_UP").getInt(null);
            type.getMethod("type", int.class).invoke(event, kind);
            Object key = type.getMethod("key").invoke(event);
            key.getClass().getMethod("type", int.class).invoke(key, kind);
            key.getClass().getMethod("scancode", int.class).invoke(key, scancode);
            key.getClass().getMethod("down", boolean.class).invoke(key, down);
            key.getClass().getMethod("windowID", int.class).invoke(key, windowId());
            require(Boolean.TRUE.equals(events.getMethod("SDL_PushEvent", type).invoke(null, event)), "Native key event was rejected");
        } finally { type.getMethod("free").invoke(event); }
    }

    private static void pushText(String value) throws Exception {
        var type = Class.forName("org.lwjgl.sdl.SDL_Event");
        var events = Class.forName("org.lwjgl.sdl.SDLEvents");
        Object event = type.getMethod("calloc").invoke(null);
        try {
            int kind = events.getField("SDL_EVENT_TEXT_INPUT").getInt(null);
            type.getMethod("type", int.class).invoke(event, kind);
            Object text = type.getMethod("text").invoke(event);
            text.getClass().getMethod("type", int.class).invoke(text, kind);
            text.getClass().getMethod("windowID", int.class).invoke(text, windowId());
            ByteBuffer buffer = (ByteBuffer) Class.forName("org.lwjgl.system.MemoryUtil").getMethod("memUTF8", CharSequence.class).invoke(null, value);
            textBuffers.add(buffer);
            text.getClass().getMethod("text", ByteBuffer.class).invoke(text, buffer);
            require(Boolean.TRUE.equals(events.getMethod("SDL_PushEvent", type).invoke(null, event)), "Native Japanese text event was rejected");
        } finally { type.getMethod("free").invoke(event); }
    }

    private static void screenshot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget());
    }

    private static void finish(Throwable error) {
        if (done) return;
        done = true;
        hoveredSlot = null;
        result.put("passed", error == null);
        result.put("synthetic", true);
        result.put("stage", stage);
        result.put("nativeKeys", List.copyOf(nativeKeys));
        result.put("slotClicks", List.copyOf(slotClicks));
        if (Minecraft.getInstance().player != null) {
            var inventory = Minecraft.getInstance().player.inventory;
            result.put("finalHotbar", List.of(String.valueOf(inventory.getItem(0)), String.valueOf(inventory.getItem(8))));
        }
        if (chest != null) result.put("finalChestSlot", String.valueOf(chest.getItem(0)));
        if (error != null) { result.put("error", error.toString()); error.printStackTrace(); }
        try {
            Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            for (var buffer : textBuffers) Class.forName("org.lwjgl.system.MemoryUtil").getMethod("memFree", java.nio.Buffer.class).invoke(null, buffer);
        } catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
