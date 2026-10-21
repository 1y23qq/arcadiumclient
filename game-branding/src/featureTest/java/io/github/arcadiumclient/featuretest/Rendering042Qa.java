// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;

public final class Rendering042Qa {
    public static final UUID PLAYER = new UUID(0, 42);
    private static final String FEATURES = "io.github.arcadiumclient.runtime.client.features.";
    private static final String PREFS = "org.polyfrost.oneconfig.internal.OneConfigConfig";
    private static final String MANAGER = "org.polyfrost.oneconfig.api.hud.v1.HudManager";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> FRAMES = new ArrayList<>();
    private static final List<Map<String, Object>> DRAW = new ArrayList<>();
    private static final List<Map<String, Object>> BACKGROUNDS = new ArrayList<>();
    private static final List<Map<String, Object>> BADGES = new ArrayList<>();
    private static Object height, health, manager;
    private static QaPlayer dummy;
    private static int ticks, ready, phase;
    private static boolean done, capture;
    public static boolean badge = true;

    private Rendering042Qa() { }
    public static boolean enabled() { return Boolean.getBoolean("arcadium.rendering042"); }

    public static void tick() {
        if (!enabled() || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require(++ticks < 1600, "Rendering QA timed out at phase " + phase);
            if (ticks == 1) {
                require(mc.getSession().getUsername().equals("ArcadiumTest") && mc.getSession().getAccessToken().equals("0"), "QA requires its synthetic offline account");
                mc.options.pauseOnUnfocus = false; mc.options.viewDistance = 4; mc.options.guiScale = 2;
                mc.options.fpsLimit = 260;
                RESULT.put("syntheticSession", true); RESULT.put("actualWindowFocused", Display.isActive());
            }
            if (ticks == 60) mc.startGame("rendering042", "Rendering QA", new WorldSettings(71, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
            if (mc.world == null || mc.player == null) return;
            if (++ready == 80) setup(mc);
            if (ready < 80) return;
            mc.player.abilities.flying = true; mc.player.setHealth(17f);
            int distance = new int[]{20, 15, 6, 5, 0, -4}[Math.min(5, phase)];
            mc.player.setPositionAndAngles(8, 113 - distance, 8, 0, 0);
            mc.player.lastX = mc.player.x; mc.player.lastY = mc.player.y; mc.player.lastZ = mc.player.z;
            dummy.setPositionAndAngles(8, mc.player.y, 12, 180, 0);
            dummy.lastX = dummy.x; dummy.lastY = dummy.y; dummy.lastZ = dummy.z;
            var source = Class.forName(FEATURES + "HeightLimitSource");
            for (var entry : Map.of("lastWorld", mc.world, "sampledAt", Long.MAX_VALUE / 2).entrySet()) {
                var field = source.getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(null, entry.getValue());
            }
            if (ready % 36 != 7) return;
            call(height, "updateAndRecalculate"); call(health, "updateAndRecalculate");
            require(call(health, "getText").equals(phase >= 5 ? "❤17" : "❤8.5"), "Health units did not match current health: " + call(health, "getText"));
            if (phase <= 5) {
                require(call(height, "getText").equals("113(" + Math.max(0, distance) + ")"), "Height Limit text is incorrect");
                populateChat(mc);
                capture = true;
                if (phase == 4) call(health, "setUnits", 1);
                phase++;
                return;
            }
            if (phase == 6) {
                Object poppins = Enum.valueOf((Class) Class.forName("org.polyfrost.oneconfig.api.hud.v1.Font"), "Poppins");
                call(height, "setFont", poppins); call(health, "setFont", poppins);
            } else if (phase == 7) {
                staticField(PREFS, "useInterForModText").setBoolean(null, true);
                stat("org.polyfrost.oneconfig.internal.FontPreferences", "refresh");
            } else if (phase == 8) {
                staticField(PREFS, "useInterInGame").setBoolean(null, true);
                stat("org.polyfrost.oneconfig.internal.FontPreferences", "refresh");
            } else if (phase == 9) {
                staticField(PREFS, "useInterInGame").setBoolean(null, false);
                staticField(PREFS, "useVanillaFontInGame").setBoolean(null, true);
                stat("org.polyfrost.oneconfig.internal.FontPreferences", "refresh");
                badge = false;
            } else if (phase == 10) {
                set(height, "style", 2); set(height, "format", "#current/#limit (#distance)");
                set(height, "limitColor", 0xFF36F1B0); set(height, "parenthesesColor", 0xFF3AA0FF); set(height, "distanceNearColor", 0xFFED5D9A);
                call(height, "updateAndRecalculate");
                require(call(height, "getText").equals("117/113 (0)"), "Custom Height Limit tokens did not render");
            } else if (phase == 11) {
                ownWindow("SDL_MinimizeWindow"); phase++; return;
            } else if (phase == 12) {
                require(!Display.isActive(), "The QA window did not lose focus after minimizing itself");
                testFps(mc);
                ownWindow("SDL_RestoreWindow");
                RESULT.put("minimizedWindowLimitVerified", true);
                RESULT.put("frames", FRAMES); RESULT.put("passed", true);
                finish(mc);
                return;
            }
            call(height, "updateAndRecalculate"); call(health, "updateAndRecalculate");
            populateChat(mc);
            capture = true; phase++;
        } catch (Throwable failure) {
            failure.printStackTrace(); RESULT.put("passed", false); RESULT.put("error", failure.toString());
            try { finish(mc); } catch (Exception error) { error.printStackTrace(); }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setup(Minecraft mc) throws Exception {
        manager = object(MANAGER);
        height = makeHud("HeightLimitHud", 20f, 50f);
        health = makeHud("HealthHud", 20f, 90f);
        set(height, "style", 1);
        var source = Class.forName(FEATURES + "HeightLimitSource");
        var value = source.getDeclaredField("value"); value.setAccessible(true);
        value.set(null, Class.forName(FEATURES + "HeightLimitSource$Snapshot").getConstructor(boolean.class, String.class, Integer.class, boolean.class).newInstance(true, "QA", 113, true));
        dummy = new QaPlayer(mc.world, new GameProfile(PLAYER, "ArcadiumDummy"));
        mc.world.forceEntity(4242, dummy);
        stat(FEATURES + "ChatTweaksConfig", "setCompactChat", true);
        stat(FEATURES + "ChatTweaksConfig", "setCompactChatFormat", 4);
        stat(FEATURES + "ChatTweaksConfig", "setTimestamps", true);
        populateChat(mc);
        mc.openScreen(new ChatScreen(""));
    }

    private static void populateChat(Minecraft mc) {
        mc.gui.getChat().clear();
        for (int i = 0; i < 3; i++) {
            mc.gui.getChat().addMessage(new LiteralText("You purchased ").setStyle(new Style().setColor(Formatting.GREEN))
                .append(new LiteralText("Wool").setStyle(new Style().setColor(Formatting.GOLD).setBold(true))));
        }
        mc.gui.getChat().addMessage(new LiteralText("You don't have enough Iron! Need 4 more!").setStyle(new Style().setColor(Formatting.RED).setBold(true)));
        mc.gui.getChat().addMessage(new LiteralText("You don't have enough Iron! Need 4 more!").setStyle(new Style().setColor(Formatting.RED).setBold(true)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object makeHud(String type, float x, float y) throws Exception {
        Object provider = Class.forName(FEATURES + type).getConstructor().newInstance();
        Object hud = call(provider, "make", (Object) null);
        call(manager, "markProviderKnown", hud); ((List) call(manager, "getActiveInstances")).add(hud); call(hud, "setup");
        call(hud, "setHidden", false); call(hud, "setShowBackground", true); call(hud, "setShowShadow", false);
        call(hud, "setUseGuiScale", false); call(hud, "setCustomScale", 1f); call(hud, "setTextScale", 2f);
        call(hud, "setAbsolutePosition", x, y);
        return hud;
    }

    private static void set(Object hud, String name, Object value) throws Exception {
        call(call(call(hud, "getTree"), "getProp", name), "setAsReferential", value);
    }

    private static void testFps(Minecraft mc) throws Exception {
        List<Map<String, Object>> cases = new ArrayList<>();
        RESULT.put("unfocusedFps", cases);
        for (int requested : new int[]{5, 60, 137, 200, 2, 1000}) {
            staticField(PREFS, "unfocusedFps").setInt(null, requested);
            int unfocused = ((Number) stat(FEATURES + "UnfocusedFrameLimit", "apply", 260, false, requested)).intValue();
            require(unfocused == Math.max(5, Math.min(200, requested)), "Unfocused FPS cap did not apply");
            int active = ((Number) stat(FEATURES + "UnfocusedFrameLimit", "apply", 260, true, requested)).intValue();
            require(active == 260, "The unfocused cap affected the focused game");
            boolean before = Display.isActive();
            int live = mc.getMaxFramerate();
            boolean after = Display.isActive();
            cases.add(Map.of("requested", requested, "unfocusedLimit", unfocused, "focusedLimit", active, "liveLimit", live));
            require(before != after || live == (after ? 260 : unfocused), "The live window focus did not select the correct FPS cap: requested=" + requested + ", live=" + live + ", before=" + before + ", after=" + after);
        }
        staticField(PREFS, "unfocusedFps").setInt(null, 60);
        RESULT.put("unfocusedFps", cases);
    }

    private static void ownWindow(String method) throws Exception {
        Object window = stat("pl.tomgirl.pylon.window.DisplaySdl", "instance");
        long handle = ((Number) call(window, "getHandle")).longValue();
        Class.forName("org.lwjgl.sdl.SDLVideo").getMethod(method, long.class).invoke(null, handle);
    }

    public static void draw(String text, float x, float y, float end, boolean shadow) {
        if (!enabled() || shadow || text == null || DRAW.size() > 300) return;
        DRAW.add(Map.of("text", text, "x", x, "y", y, "end", end));
    }
    public static void background(float x, float y, float width) {
        if (enabled() && BACKGROUNDS.size() < 100) BACKGROUNDS.add(Map.of("x", x, "y", y, "width", width));
    }
    public static void badge(float x, float y) {
        if (enabled() && BADGES.size() < 100) BADGES.add(Map.of("x", x, "y", y));
    }

    public static void afterFrame() {
        if (!enabled() || done) return;
        try {
            if (capture) {
                var mc = Minecraft.getInstance();
                String name = "rendering042-" + (phase - 1) + ".png";
                Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget());
                FRAMES.add(Map.of("phase", phase - 1, "screenshot", name, "text", List.copyOf(DRAW), "backgrounds", List.copyOf(BACKGROUNDS), "badges", List.copyOf(BADGES)));
                capture = false;
            }
        } catch (Throwable failure) { failure.printStackTrace(); RESULT.put("error", failure.toString()); }
        DRAW.clear(); BACKGROUNDS.clear(); BADGES.clear();
    }

    private static void finish(Minecraft mc) throws Exception {
        RESULT.put("frames", FRAMES);
        Files.writeString(mc.gameDir.toPath().resolve("rendering042-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));
        done = true; mc.stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
