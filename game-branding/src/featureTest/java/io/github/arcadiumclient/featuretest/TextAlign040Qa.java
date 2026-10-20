// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class TextAlign040Qa {
    private static final List<Object> HUDS = new ArrayList<>();
    private static final List<Map<String, Object>> CHECKS = new ArrayList<>();
    private static final String[] FONTS = {"Minecraft", "Inter", "Poppins"};
    private static int stage, font, entered;
    private static boolean waiting, complete, previousInter;
    private static Object manager;
    private static String capture;
    private static final Map<String, Long> PENDING = new LinkedHashMap<>();

    private TextAlign040Qa() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean tick(Minecraft client, Map<String, Object> result, int tick) throws Exception {
        if (complete) return true;
        if (stage == 0) {
            result.put("renderedTextAlignment", CHECKS);
            manager = object("org.polyfrost.oneconfig.api.hud.v1.HudManager");
            previousInter = Class.forName("org.polyfrost.oneconfig.internal.OneConfigConfig").getField("useInterForModText").getBoolean(null);
            Object section = Enum.valueOf((Class) Class.forName("org.polyfrost.oneconfig.api.hud.v1.Section"), "TopLeft");
            Object align = Enum.valueOf((Class) Class.forName("org.polyfrost.compose.layout.PolyAlign"), "TopLeft");
            for (int column = 0; column < 3; column++) {
                Object provider = Class.forName("org.polyfrost.oneconfig.api.hud.v1.TextHud$Simple").getConstructor(String.class, String.class, String.class).newInstance("", "MMMMMMMM\nMM", "");
                Object hud = call(provider, "make", (Object) null);
                call(manager, "markProviderKnown", hud);
                ((List) call(manager, "getActiveInstances")).add(hud);
                call(hud, "setup");
                call(hud, "setSection", section); call(hud, "setAlignment", align);
                call(hud, "setRelativeX", 20f + column * 90f); call(hud, "setRelativeY", 80f);
                call(hud, "setUseGuiScale", false); call(hud, "setCustomScale", 1f);
                call(hud, "setStaticWidth", true); call(hud, "setStaticW", 160f); call(hud, "setStaticH", 64f);
                call(hud, "setPadLeft", 8f); call(hud, "setPadRight", 8f); call(hud, "setPadTop", 8f); call(hud, "setPadBottom", 8f);
                call(hud, "setTextScale", 2f); call(hud, "setTextAlign", column); call(hud, "setTextColor", 0xFFFFFFFF);
                call(hud, "setTextChroma", false); call(hud, "setShowShadow", false); call(hud, "setShowBackground", true);
                call(hud, "setBgColor", 0xFF101419); call(hud, "setBgChroma", false); call(hud, "setMergeBackground", false);
                call(hud, "setHidden", false); HUDS.add(hud);
            }
            Object revision = call(manager, "getInstancesRevision");
            call(revision, "setIntValue", ((Number) call(revision, "getIntValue")).intValue() + 1);
            configure(); entered = tick; stage = 1;
            return false;
        }
        if (stage == 1) {
            if (waiting || tick - entered < 20) return false;
            capture = "040-189-text-align-" + FONTS[font].toLowerCase() + ".png";
            waiting = true;
        } else if (stage == 2) {
            if (++font < FONTS.length) { configure(); entered = tick; stage = 1; }
            else {
                for (Object hud : HUDS) call(hud, "setHidden", true);
                setInter(previousInter);
                stat("org.polyfrost.oneconfig.api.hud.v1.HudManager", "invalidate");
                require(CHECKS.size() == 9, "The nine rendered text alignment cases did not complete");
                complete = true;
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void configure() throws Exception {
        setInter(FONTS[font].equals("Inter"));
        Object type = Enum.valueOf((Class) Class.forName("org.polyfrost.oneconfig.api.hud.v1.Font"), FONTS[font].equals("Poppins") ? "Poppins" : "Minecraft");
        for (Object hud : HUDS) { call(hud, "setFont", type); call(hud, "updateAndRecalculate"); }
        stat("org.polyfrost.oneconfig.api.hud.v1.HudManager", "invalidate");
    }

    private static void setInter(boolean enabled) throws Exception {
        Object preferences = call(object("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry"), "findTree", "oneconfig.json");
        call(call(preferences, "getProp", "useInterForModText"), "setAsReferential", enabled);
    }

    private static void afterCapture(Minecraft client, String name, BufferedImage image) throws Exception {
        if (!waiting || !name.startsWith("040-189-text-align-")) return;
        Field widthField = Class.forName("org.polyfrost.oneconfig.api.hud.v1.HudManager").getField("guiScreenWidth");
        double guiScale = client.width / widthField.getFloat(null);
        for (int alignment = 0; alignment < HUDS.size(); alignment++) {
            Object hud = HUDS.get(alignment);
            int left = (int) Math.round(((Number) call(hud, "getX")).floatValue() * guiScale);
            int top = (int) Math.round(((Number) call(hud, "getY")).floatValue() * guiScale);
            List<int[]> lines = whiteLines(image, left, top, 160, 64);
            require(lines.size() == 2, "The actual " + FONTS[font] + " HUD did not show exactly two text lines: " + lines.size());
            int[] first = lines.get(0), second = lines.get(1);
            int actual = second[0] - first[0];
            double expected = ((first[1] - first[0]) - (second[1] - second[0])) * alignment * .5;
            require(Math.abs(actual - expected) <= 2.1, FONTS[font] + " Text Align " + alignment + " drew the second line at " + actual + "px, expected " + expected);
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("font", FONTS[font]); check.put("alignment", List.of("Left", "Center", "Right").get(alignment));
            check.put("secondLineOffsetPixels", actual); check.put("expectedOffsetPixels", expected); check.put("fixedWidthPixels", 160);
            check.put("bounds", List.of(left, top, 160, 64)); check.put("screenshot", name); CHECKS.add(check);
        }
        waiting = false; stage = 2;
    }

    private static List<int[]> whiteLines(BufferedImage image, int x0, int y0, int width, int height) {
        List<int[]> lines = new ArrayList<>();
        int minX = Integer.MAX_VALUE, maxX = -1;
        boolean active = false;
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y0 + height); y++) {
            boolean ink = false;
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x0 + width); x++) {
                int color = image.getRGB(x, y);
                if ((color >>> 16 & 255) >= 180 && (color >>> 8 & 255) >= 180 && (color & 255) >= 180) { ink = true; minX = Math.min(minX, x); maxX = Math.max(maxX, x); }
            }
            if (!ink && active) { lines.add(new int[]{minX, maxX}); minX = Integer.MAX_VALUE; maxX = -1; }
            active = ink;
        }
        if (active) lines.add(new int[]{minX, maxX});
        return lines;
    }
    public static void afterFrame() throws Exception {
        Minecraft client = Minecraft.getInstance();
        if (capture != null) {
            net.minecraft.client.Screenshot.take(client.gameDir, capture, client.width, client.height, client.getRenderTarget());
            PENDING.put(capture, System.nanoTime());
            capture = null;
        }
        for (String name : new ArrayList<>(PENDING.keySet())) {
            require(System.nanoTime() - PENDING.get(name) < 15_000_000_000L, "Text Align screenshot timed out: " + name);
            var file = client.gameDir.toPath().resolve("screenshots").resolve(name);
            if (!java.nio.file.Files.isRegularFile(file)) continue;
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            if (bytes.length < 24 || bytes[bytes.length - 8] != 'I' || bytes[bytes.length - 7] != 'E' || bytes[bytes.length - 6] != 'N' || bytes[bytes.length - 5] != 'D') continue;
            afterCapture(client, name, javax.imageio.ImageIO.read(file.toFile()));
            PENDING.remove(name);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

}
