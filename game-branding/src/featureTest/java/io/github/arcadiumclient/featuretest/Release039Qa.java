// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.VideoOptionsScreen;

final class Release039Qa {
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static Screen menu;
    private static int stage, ticks;
    private static boolean begun;

    private Release039Qa() { }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!begun) {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
                "Release QA requires the isolated offline account");
            checks.put("release039", RESULT);
            Object active = stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active");
            Path folder = ((Path) call(active, "getFolder")).toAbsolutePath().normalize();
            require(folder.startsWith(mc.gameDir.toPath().toRealPath()), "Release QA cannot use a real profile");
            Object registry = object("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry");
            Object preferences = call(registry, "findTree", "oneconfig.json");
            Object mode = call(preferences, "getProp", "guiMode");
            int expected = Integer.getInteger("arcadium.release039ExpectedMode", 1);
            require(((Number) call(mode, "get")).intValue() == expected, "Stored GUI mode was changed");
            require(((Number) call(mode, "getMetadata", "default")).intValue() == 1, "New users do not default to Simple");
            require((boolean) call(object("org.polyfrost.oneconfig.internal.ui.shell.ShellState"), "getSimpleMode") == (expected == 1),
                "GUI mode was not applied to the live menu");
            float expectedScroll = Float.parseFloat(System.getProperty("arcadium.release039ExpectedScroll", "2.5"));
            require(Math.abs(((Number) call(call(preferences, "getProp", "scrollSpeed"), "get")).floatValue() - expectedScroll) < .001f,
                "An unrelated preference was changed");
            call(active, "save", "oneconfig.json");
            var saved = JsonParser.parseString(Files.readString(folder.resolve("oneconfig.json"))).getAsJsonObject();
            require(saved.get("guiMode").getAsInt() == expected, "GUI mode was not persisted");
            RESULT.put("mode", expected);
            RESULT.put("simpleDefault", true);
            RESULT.put("storedModeAndOtherPreferencesPreserved", true);
            RESULT.put("preferencePersisted", true);
            Object client = call(registry, "findById", "arcadium-client.json");
            require(client != null && !(boolean) call(registry, "shouldShowInSearch", client), "Client is still searchable as a module");
            for (Object config : (Collection<?>) call(registry, "getModCardConfigs"))
                require(!"arcadium-client.json".equals(call(config, "getId")), "Client is still in the mod menu");
            Object badge = call(registry, "findTree", "arcadium-players.json");
            require("Arcadium Badge".equals(call(badge, "getTitle")), "Badge module was not renamed");
            RESULT.put("clientHiddenFromModsAndSearch", true);
            RESULT.put("badgeName", "Arcadium Badge");
            menu = mc.screen;
            require("ArcadiumMainMenu".equals(menu.getClass().getSimpleName()), "QA did not start on the title screen");
            require((boolean) call(menu, "getShadesPanorama"), "Title and Options panorama shading differ");
            require(((Number) call(menu, "getPanoramaBlurRadius")).floatValue() == 8f, "Title and Options blur radii differ");
            begun = true;
            if (Boolean.getBoolean("arcadium.release039QuickQa")) {
                RESULT.put("completed", true);
                return true;
            }
        }
        if (++ticks < 20) return false;
        ticks = 0;
        switch (stage++) {
            case 0 -> {
                capture(mc, "release039-title.png");
                click(menu, "Arcadium Options");
            }
            case 1 -> {
                require("OneConfigUIScreen".equals(mc.screen.getClass().getSimpleName()), "Arcadium Options did not open");
                require((boolean) call(mc.screen, "getShadesPanorama"), "Options does not use the same panorama shading");
                require(((Number) call(mc.screen, "fullscreenBlurRadius")).floatValue() == 8f, "Title and Options blur differ");
                capture(mc, "release039-options.png");
                RESULT.put("titleAndOptionsBackdropMatch", true);
                mc.openScreen(menu);
            }
            case 2 -> {
                staticField("org.polyfrost.oneconfig.internal.OneConfigConfig", "enableBackgroundBlur").setBoolean(null, false);
                require(((Number) call(menu, "getPanoramaBlurRadius")).floatValue() == 0f, "Title ignores the blur preference");
                staticField("org.polyfrost.oneconfig.internal.OneConfigConfig", "enableBackgroundBlur").setBoolean(null, true);
                mc.openScreen(new VideoOptionsScreen(menu, mc.options));
            }
            case 3 -> {
                require("ArcadiumVideoSettingsScreen".equals(mc.screen.getClass().getSimpleName()), "Video settings did not open");
                Object model = field(mc.screen, "model");
                Object fov = null;
                for (Object setting : (Collection<?>) call(model, "getOptions"))
                    if ("dynamic_fov".equals(call(setting, "getKey"))) fov = setting;
                require(fov != null && "SLIDER".equals(call(fov, "getControl").toString()), "Dynamic FOV slider is missing");
                Object original = call(fov, "getValue");
                call(fov, "change", call(fov, "getMinimum"));
                call(model, "apply");
                require(call(fov, "getValue").equals(call(fov, "getMinimum")), "Dynamic FOV cannot be disabled");
                call(fov, "change", original);
                call(model, "apply");
                capture(mc, "release039-video.png");
                RESULT.put("videoDynamicFovSliderRetained", true);
                RESULT.put("completed", true);
                return true;
            }
            default -> throw new IllegalStateException("Invalid release QA stage");
        }
        return false;
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
