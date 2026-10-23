// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class LevelHead045Qa {
    public static final UUID PLAYER = UUID.fromString("12345678-1234-4234-9234-000000000045");
    private static final String FEATURES = "io.github.arcadiumclient.runtime.client.features.";
    private static final String BEDWARS = "io.github.arcadiumclient.runtime.client.bedwars.";
    private static final String PREFS = "org.polyfrost.oneconfig.internal.OneConfigConfig";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> CASES = new ArrayList<>();
    private static QaPlayer dummy;
    private static ServerListEntry previousServer;
    private static Object data;
    private static int ticks, stage, calls, textDraws, backgrounds;
    private static boolean begun, ready, prepared, done, probing;
    private static String capture;
    private static volatile Throwable renderFailure;

    private LevelHead045Qa() { }

    public static boolean tick(Minecraft mc) throws Exception {
        if (done) return true;
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
                    "LevelHead QA requires its isolated offline account");
            require(++ticks < 70, "LevelHead QA timed out at stage " + stage);
            if (renderFailure != null) throw new IllegalStateException("LevelHead rendering failed", renderFailure);
            if (!begun) {
                begun = true;
                previousServer = mc.getCurrentServerEntry();
                mc.options.pauseOnUnfocus = false; mc.options.viewDistance = 4; mc.options.guiScale = 2;
                mc.options.fpsLimit = 120; mc.options.perspective = 0; mc.options.hideGui = false;
                findField(object(BEDWARS + "BedwarsOverlayConfig").getClass(), "enabled")
                        .setBoolean(object(BEDWARS + "BedwarsOverlayConfig"), false);
                mc.openScreen(null);
                if (mc.world == null) {
                    mc.startGame("levelhead045", "LevelHead QA", new WorldSettings(45, WorldSettings.GameMode.CREATIVE,
                            false, false, WorldGeneratorType.FLAT).enableCommands());
                    return false;
                }
            }
            if (mc.world == null || mc.player == null) return false;
            require(mc.isIntegratedServerRunning(), "The LevelHead fixture must use an offline integrated world");
            if (!ready) setup(mc);
            pin(mc);
            if (capture != null) return false;
            if (stage >= 4) {
                for (Map<String, Object> entry : CASES)
                    if (!Files.isRegularFile(mc.gameDir.toPath().resolve("screenshots").resolve((String) entry.get("screenshot")))) return false;
                verifyPixels(mc);
                require(((Number) call(data, "pendingCount")).intValue() == 0, "The cached LevelHead fixture queued a live statistics request");
                stat(FEATURES + "LevelHeadConfig", "setEnabled", false);
                staticField(PREFS, "useInterInGame").setBoolean(null, false);
                stat("org.polyfrost.oneconfig.internal.FontPreferences", "refresh");
                mc.setCurrentServerEntry(previousServer);
                RESULT.put("cases", CASES); RESULT.put("noStatisticsRequests", true); RESULT.put("passed", true);
                write(mc);
                done = true;
                return true;
            }
            if (!prepared) {
                prepared = true;
                stat(FEATURES + "LevelHeadConfig", "setLevel", stage >= 2 ? 1 : 0);
                stat(FEATURES + "LevelHeadConfig", "setBackground", stage % 2 == 0);
                staticField(PREFS, "useInterInGame").setBoolean(null, stage == 3);
                stat("org.polyfrost.oneconfig.internal.FontPreferences", "refresh");
                calls = textDraws = backgrounds = 0;
                return false;
            }
            require(calls > 1 && textDraws > 1, "The actual entity rendering pipeline did not draw LevelHead at stage " + stage);
            require(stage % 2 == 0 ? backgrounds > 1 : backgrounds == 0, "The LevelHead background toggle did not change its geometry");
            String name = "levelhead045-" + (stage >= 2 ? "bedwars" : "hypixel") + "-background-" + (stage % 2 == 0 ? "on" : "off") + ".png";
            CASES.add(Map.of("mode", stage >= 2 ? "Bedwars" : "Hypixel", "text", stage >= 2 ? "Bedwars Level 245" : "Level 50",
                    "background", stage % 2 == 0, "interFont", stage == 3, "renderCalls", calls,
                    "textDraws", textDraws, "backgroundQuads", backgrounds, "glStateRestored", true, "screenshot", name));
            capture = name; prepared = false; stage++;
            return false;
        } catch (Throwable failure) {
            RESULT.put("passed", false); RESULT.put("error", failure.toString()); RESULT.put("cases", CASES);
            try { write(mc); } catch (Exception ignored) { }
            if (failure instanceof Exception exception) throw exception;
            throw new IllegalStateException(failure);
        }
    }

    private static void setup(Minecraft mc) throws Exception {
        data = stat(BEDWARS + "HypixelDataService", "data");
        call(data, "clear");
        cache(PLAYER, "LevelHeadDummy");
        cache(mc.player.getUuid(), "ArcadiumTest");
        dummy = new QaPlayer(mc.world, new GameProfile(PLAYER, "LevelHeadDummy"));
        mc.world.forceEntity(4545, dummy);
        mc.setCurrentServerEntry(new ServerListEntry("Offline LevelHead fixture", "qa.hypixel.net", false));
        mc.getServer().executeTask(() -> {
            var world = mc.getServer().getWorld(0);
            world.getGameRules().set("doMobSpawning", "false");
            world.getGameRules().set("doDaylightCycle", "false");
            world.setTimeOfDay(6000);
            mc.getServer().getPlayerManager().getAll().get(0).teleport(8, 4, 8);
        });
        stat(FEATURES + "NoRenderConfig", "setEnabled", false);
        stat(FEATURES + "NickHiderConfig", "setEnabled", false);
        stat(FEATURES + "LevelHeadConfig", "setScale", 1.5f);
        stat(FEATURES + "LevelHeadConfig", "setHeightOffset", 0.08f);
        stat(FEATURES + "LevelHeadConfig", "setEnabled", true);
        staticField(PREFS, "useVanillaFontInGame").setBoolean(null, false);
        emojiChecks(mc);
        RESULT.put("syntheticSession", true); RESULT.put("integratedWorld", true); RESULT.put("fixtureNetworkLevel", 50);
        RESULT.put("fixtureBedwarsLevel", 245);
        RESULT.put("prefixColor", ((java.awt.Color) stat(FEATURES + "LevelHeadConfig", "getPrefixColor")).getRGB());
        RESULT.put("suffixColor", ((java.awt.Color) stat(FEATURES + "LevelHeadConfig", "getSuffixColor")).getRGB());
        ready = true;
    }

    @SuppressWarnings("unchecked")
    private static void cache(UUID id, String name) throws Exception {
        Class<?> stats = Class.forName(BEDWARS + "BedwarsStats");
        Object value = stats.getConstructor(UUID.class, String.class, int.class, double.class, int.class, int.class,
                boolean.class, boolean.class, String.class, String.class, String.class, String.class, String.class, String.class, int.class)
                .newInstance(id, name, 245, 7.5d, 30, 4, false, false, null, null, null, null, null, null, 50);
        var constructor = Class.forName(BEDWARS + "BedwarsData$Cached").getDeclaredConstructor(stats, long.class);
        constructor.setAccessible(true);
        synchronized (data) { ((Map<UUID, Object>) field(data, "cache")).put(id, constructor.newInstance(value, Long.MAX_VALUE)); }
    }

    private static void pin(Minecraft mc) {
        mc.openScreen(null);
        mc.player.abilities.flying = true;
        mc.player.setPositionAndAngles(8, 4, 8, 0, 0);
        mc.player.lastX = mc.player.prevX = mc.player.x;
        mc.player.lastY = mc.player.prevY = mc.player.y;
        mc.player.lastZ = mc.player.prevZ = mc.player.z;
        dummy.setPositionAndAngles(8, 4, 12, 180, 0);
        dummy.lastX = dummy.prevX = dummy.x;
        dummy.lastY = dummy.prevY = dummy.y;
        dummy.lastZ = dummy.prevZ = dummy.z;
    }

    private static void emojiChecks(Minecraft mc) throws Exception {
        String emoji = "org.polyfrost.oneconfig.internal.emoji.EmojiText";
        stat(emoji, "setEnabled", true);
        String glyph = (String) stat(emoji, "display", ":smile:");
        int advance = staticField(emoji, "ADVANCE").getInt(null);
        require(glyph.length() == 1 && mc.textRenderer.getWidth(":smile:") == advance, "Emoji shortcode width did not use its rendered glyph");
        require(mc.textRenderer.getWidth("A :smile: B") == mc.textRenderer.getWidth("A " + glyph + " B"), "Emoji encoded and source widths differ");
        require(mc.textRenderer.getWidth("§l:smile:") == advance + 1, "Bold emoji width lost its extra advance");
        require(mc.textRenderer.trim(":smile:XYZ", advance).equals(":smile:"), "Emoji trim split a shortcode");
        RESULT.put("emojiWidthAndTrim", true);
    }

    public static Object beforeRender(Entity entity) {
        if (!ready || done || !prepared || !PLAYER.equals(entity.getUuid())) return null;
        probing = true;
        return new Snapshot();
    }

    public static void afterRender(Object before) {
        if (before == null) return;
        try {
            require(((Snapshot) before).same(new Snapshot()), "LevelHead leaked GL state from an actual world entity render");
            calls++;
        } catch (Throwable failure) { renderFailure = failure; }
        finally { probing = false; }
    }

    public static void text(String text, float start, float y, float end, boolean shadow) {
        if (!probing || shadow || text == null) return;
        try {
            String prefix = stage >= 2 ? "Bedwars Level " : "Level ", value = stage >= 2 ? "245" : "50";
            require(text.equals(prefix) || text.equals(value), "Unexpected LevelHead text: " + text);
            int width = Minecraft.getInstance().textRenderer.getWidth(text);
            int total = Minecraft.getInstance().textRenderer.getWidth(prefix + value);
            int offset = text.equals(prefix) ? 0 : Minecraft.getInstance().textRenderer.getWidth(prefix);
            require(Math.abs(start + total / 2f - offset) < 0.01f && Math.abs(y) < 0.01f, "LevelHead text was not centered above its player");
            require(Math.abs(end - start - width) <= 1.01f, "LevelHead text width differs from its rendered bounds");
            java.awt.Color target = (java.awt.Color) stat(FEATURES + "LevelHeadConfig", text.equals(prefix) ? "getPrefixColor" : "getSuffixColor");
            float[] color = Snapshot.floats(GL11.GL_CURRENT_COLOR, 4);
            require(Math.abs(color[0] - target.getRed() / 255f) < 0.01f && Math.abs(color[1] - target.getGreen() / 255f) < 0.01f
                    && Math.abs(color[2] - target.getBlue() / 255f) < 0.01f, "LevelHead drew with an incorrect cached text color");
            textDraws++;
        } catch (Throwable failure) { renderFailure = failure; }
    }

    public static void background() { if (probing) backgrounds++; }

    public static boolean blockUnexpectedRequest() {
        if (!begun || done) return false;
        renderFailure = new IllegalStateException("The offline LevelHead fixture attempted a live statistics lookup");
        return true;
    }

    public static void afterFrame() {
        if (capture == null || done) return;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.take(mc.gameDir, capture, mc.width, mc.height, mc.getRenderTarget());
        capture = null;
    }

    private static void write(Minecraft mc) throws Exception {
        Files.writeString(mc.gameDir.toPath().resolve("levelhead045-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));
    }

    private static void verifyPixels(Minecraft mc) throws Exception {
        List<Map<String, Object>> images = new ArrayList<>();
        for (Map<String, Object> entry : CASES) {
            String name = (String) entry.get("screenshot");
            BufferedImage image = ImageIO.read(mc.gameDir.toPath().resolve("screenshots").resolve(name).toFile());
            require(image != null, "The LevelHead screenshot could not be read");
            int prefix = 0, suffix = 0;
            for (int y = image.getHeight() / 8; y < image.getHeight() / 2; y++)
                for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x++) {
                    int color = image.getRGB(x, y), red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
                    if (Math.abs(red - 85) < 18 && green > 230 && blue > 230) prefix++;
                    if (red > 230 && green > 230 && Math.abs(blue - 85) < 18) suffix++;
                }
            require(prefix >= 8 && suffix >= 8, "LevelHead glyph pixels were dark or missing: " + name + ", aqua=" + prefix + ", yellow=" + suffix);
            images.add(Map.of("screenshot", name, "aquaPixels", prefix, "yellowPixels", suffix));
        }
        RESULT.put("renderedPixelColors", images);
    }

    private static final class Snapshot {
        final int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        final int modelDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH), projectionDepth = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH);
        final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final float[] color = floats(GL11.GL_CURRENT_COLOR, 4), model = floats(GL11.GL_MODELVIEW_MATRIX, 16), projection = floats(GL11.GL_PROJECTION_MATRIX, 16);
        boolean same(Snapshot other) {
            return texture == other.texture && mode == other.mode && modelDepth == other.modelDepth && projectionDepth == other.projectionDepth
                    && src == other.src && dst == other.dst && srcAlpha == other.srcAlpha && dstAlpha == other.dstAlpha
                    && blend == other.blend && alpha == other.alpha && depth == other.depth && lighting == other.lighting && textured == other.textured
                    && mask == other.mask && near(color, other.color) && near(model, other.model) && near(projection, other.projection);
        }
        private static float[] floats(int name, int size) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(name, buffer);
            float[] result = new float[size]; for (int index = 0; index < size; index++) result[index] = buffer.get(index);
            return result;
        }
        private static boolean near(float[] first, float[] second) {
            for (int index = 0; index < first.length; index++) if (Math.abs(first[index] - second[index]) > 0.0001f) return false;
            return true;
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
