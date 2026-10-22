// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.TitleScreen;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

public final class Background043Qa {
    private static final String PREFS = "io.github.arcadiumclient.runtime.client.MenuPreferences";
    private static final String CATALOG = "io.github.arcadiumclient.runtime.client.background.PanoramaCatalog";
    private static final String RENDERER = "io.github.arcadiumclient.runtime.client.background.MenuPanoramaRenderer";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static int ticks, stage;
    private static boolean done;
    private static String capture;

    private Background043Qa() { }
    public static boolean enabled() { return Boolean.getBoolean("arcadium.background043"); }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require(++ticks < 1600, "Background QA timed out");
            if (ticks < 80 || ticks % 24 != 0) return;
            if (stage == 0) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "A synthetic session is required");
                staticField("io.github.arcadiumclient.runtime.client.ClientPreferences", "setupComplete").setBoolean(null, true);
                mc.options.pauseOnUnfocus = false; mc.options.guiScale = 2;
                mc.openScreen(new TitleScreen());
                require(mc.screen.getClass().getSimpleName().equals("ArcadiumMainMenu"), "The Arcadium title menu did not open");
                if (Boolean.getBoolean("arcadium.background043Reload")) {
                    require("aurora-village".equals(stat(PREFS, "getTitlePanorama")), "The panorama did not persist across JVMs");
                    require(Integer.valueOf(23).equals(stat(PREFS, "getTitleBlurPercent")), "Blur did not persist across JVMs");
                    require(Boolean.FALSE.equals(stat(PREFS, "getTitleGradient")), "Gradient did not persist across JVMs");
                    RESULT.put("restartPersistence", true); capture = "background043-reloaded.png"; stage = 100; return;
                }
                require("lakeside-morning".equals(stat(PREFS, "getTitlePanorama")), "The requested default was not applied");
                require(Boolean.FALSE.equals(stat(PREFS, "getTitleUseResourcePackPanorama")), "Resource pack panorama did not default to off");
                RESULT.put("newDefault", true);
                if (Boolean.getBoolean("arcadium.background043Existing")) {
                    require(Boolean.TRUE.equals(stat(PREFS, "getHideNewerWorlds")), "An existing menu preference was lost");
                    RESULT.put("existingMenuSettingsRetained", true);
                }
            } else if (stage == 1) { capture = "background043-title-default.png";
            } else if (stage == 2) { click(mc.screen, "Background");
            } else if (stage == 3) {
                require(mc.screen.getClass().getSimpleName().equals("TitleBackgroundScreen"), "The background editor did not open");
                capture = "background043-editor.png"; call(mc.screen, "updateBlur", 0); click(mc.screen, "Gradient overlay");
            } else if (stage >= 4 && stage < 19) {
                List<?> entries = (List<?>) Class.forName(CATALOG).getField("ENTRIES").get(null);
                Object entry = entries.get(stage - 4); call(mc.screen, "choose", entry);
                require(call(entry, "id").equals(stat(PREFS, "getTitlePanorama")), "Panorama selection was not applied");
                capture = "background043-scene-" + call(entry, "id") + ".png";
            } else if (stage == 19) { click(mc.screen, "Done"); capture = "background043-title-crisp.png";
            } else if (stage == 20) { click(mc.screen, "Background");
            } else if (stage == 21) {
                click(mc.screen, "Reset to default"); call(mc.screen, "updateBlur", 100);
                click(mc.screen, "Done"); capture = "background043-title-blur100.png";
            } else if (stage == 22) {
                var packs = mc.getResourcePacks(); packs.load();
                var fixture = packs.getAvailable().stream().filter(pack -> pack.getName().equals("Background QA")).findFirst().orElseThrow();
                packs.apply(List.of(fixture)); mc.reloadResources();
                require(Boolean.TRUE.equals(stat(RENDERER, "hasResourcePackPanorama")), "The folder resource pack panorama was not found");
                RESULT.put("resourcePackDetected", true); capture = "background043-pack-disabled.png";
            } else if (stage == 23) { click(mc.screen, "Background");
            } else if (stage == 24) {
                click(mc.screen, "Use resourcepack's panorama image"); call(mc.screen, "updateBlur", 0);
                click(mc.screen, "Done"); capture = "background043-pack-enabled.png";
            } else if (stage == 25) {
                require(Boolean.TRUE.equals(stat(PREFS, "getTitleUseResourcePackPanorama")), "The resource pack switch did not apply");
                click(mc.screen, "Background");
            } else if (stage == 26) { capture = "background043-pack-preview.png";
            } else if (stage == 27) { Display.setDisplayMode(new DisplayMode(854, 480));
            } else if (stage == 28) {
                capture = "background043-editor-small.png"; PlayMenusQa.useScreen(mc.screen);
                for (String text : List.of("Blur strength", "Gradient overlay", "Use resourcepack's panorama image", "Reset to default", "Done")) {
                    Object rect = PlayMenusQa.invoke(PlayMenusQa.textNode(text, false), "getBoundsInRoot");
                    require(((Number) PlayMenusQa.invoke(rect, "getBottom")).floatValue() <= mc.height, "A background control was clipped: " + text);
                }
                RESULT.put("smallWindowControls", true);
            } else if (stage == 29) {
                click(mc.screen, "Reset to default"); call(mc.screen, "choose", stat(CATALOG, "find", "aurora-village"));
                call(mc.screen, "updateBlur", 23); click(mc.screen, "Gradient overlay"); click(mc.screen, "Done");
                var config = stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active");
                String json = Files.readString(((java.nio.file.Path) call(config, "getFolder")).resolve("arcadium-menus.json"));
                require(json.contains("aurora-village") && json.contains("23"), "The editor did not save settings");
                RESULT.put("allPanoramasSelected", 15); RESULT.put("settingsSaved", true);
            } else if (stage >= 30) { RESULT.put("passed", true); finish(mc); return; }
            stage++;
        } catch (Throwable failure) {
            failure.printStackTrace(); RESULT.put("passed", false); RESULT.put("error", failure.toString());
            try { finish(mc); } catch (Exception error) { error.printStackTrace(); }
        }
    }

    public static void afterFrame() {
        if (done || capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.take(mc.gameDir, capture, mc.width, mc.height, mc.getRenderTarget());
        RESULT.put(capture, Map.of("width", mc.width, "height", mc.height, "glError", GL11.glGetError())); capture = null;
    }

    private static void finish(Minecraft mc) throws Exception {
        Files.writeString(mc.gameDir.toPath().resolve("background043-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));
        done = true; mc.stop();
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
