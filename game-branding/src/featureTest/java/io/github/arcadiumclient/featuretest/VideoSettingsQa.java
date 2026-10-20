package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.PlayMenusQa.*;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.VideoOptionsScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

final class VideoSettingsQa {
    private static final Map<String, Object> CHECKS = new LinkedHashMap<>();
    private static final String[] PAGES = {"Details", "Quality", "Animations", "Performance", "Display", "Other"};
    private static final Gson GSON = new Gson();
    private static int stage, ticks, pageIndex, substage, width, height;
    private static Screen parent;
    private static Object screen, model, graphicsChoice, dynamicFovValue;
    private static Screen worldParent;
    private static float gamma;
    private static boolean fancy;
    private static int menuStage, menuWidth, menuHeight;
    private static boolean menuVerified, menuFullscreen;
    private static final boolean RELOAD = Boolean.getBoolean("arcadium.videoSettingsReloadQa");
    private static final boolean MENU_ONLY = Boolean.getBoolean("arcadium.videoSettingsMenuOnlyQa");

    private VideoSettingsQa() { }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
            "Video QA requires the isolated offline account");
        checks.put("videoSettings", CHECKS);
        CHECKS.put("menuOnly", MENU_ONLY);
        if (++ticks < 14) return false;
        if (MENU_ONLY && !RELOAD && !menuVerified) {
            menuProbe(mc);
            ticks = 0;
            return false;
        }
        if (screen != null && mc.screen == screen) useScreen(mc.screen);
        if (stage == 0) {
            if (!RELOAD && Boolean.getBoolean("arcadium.videoImportedScaleQa")) mc.options.guiScale = 4;
            parent = mc.screen;
            width = mc.width; height = mc.height;
            gamma = mc.options.gamma; fancy = mc.options.fancyGraphics;
            mc.openScreen(new VideoOptionsScreen(parent, mc.options));
            screen = mc.screen;
            require(screen.getClass().getSimpleName().equals("ArcadiumVideoSettingsScreen"), "The video settings entry point was not replaced: " + screen.getClass().getName());
            model = field(screen, "model");
            return next();
        }
        if (RELOAD) {
            @SuppressWarnings("unchecked")
            Map<String, Object> expected = GSON.fromJson(Files.readString(stateFile(mc)), Map.class);
            require(mc.options.fancyGraphics == (boolean) expected.get("fancyGraphics"), "Graphics did not survive a JVM restart");
            require(Math.abs(mc.options.gamma - ((Number) expected.get("gamma")).floatValue()) < 0.001f, "Brightness did not survive a JVM restart");
            require(mc.options.viewDistance == ((Number) expected.get("viewDistance")).intValue(), "Render distance did not survive a JVM restart");
            require(mc.options.guiScale == ((Number) expected.get("guiScale")).intValue(), "GUI scale did not survive a JVM restart");
            require(!Boolean.TRUE.equals(invoke(model, "getChanged")), "Reopened settings are incorrectly marked unsaved");
            capture(mc, "video-settings-reloaded.png");
            CHECKS.put("restartPersistence", true); CHECKS.put("completed", true);
            return true;
        }
        switch (stage) {
            case 1 -> {
                capture(mc, "video-settings-main.png");
                CHECKS.put("mainSemantics", semanticSummary());
                require(Boolean.FALSE.equals(invoke(screen, "getShadesPanorama")), "Video settings still shade the panorama");
                var inventory = new ArrayList<Map<String, Object>>();
                for (Object option : options()) inventory.add(Map.of("key", invoke(option, "getKey"), "label", invoke(option, "getLabel"),
                    "page", invoke(option, "getPage").toString(), "control", invoke(option, "getControl").toString(), "available", invoke(option, "getAvailable")));
                CHECKS.put("inventory", inventory);
                if (FabricLoader.getInstance().isModLoaded("argentum")) {
                    var ids = new HashSet<>();
                    boolean nativeDynamicLights = false;
                    for (Object nativePage : (List<?>) invoke(field(model, "controller"), "arcadium$pages")) {
                        for (Object nativeOption : (List<?>) invoke(nativePage, "getOptions")) {
                            Object id = invoke(nativeOption, "getId");
                            ids.add(id == null ? nativeOption : id);
                            if (id != null && "dynamic_lights".equals(invoke(id, "getPath"))) nativeDynamicLights = true;
                        }
                    }
                    int dynamicLights = 0;
                    for (Object option : options()) if ("dynamic_lights".equals(invoke(option, "getKey"))) dynamicLights++;
                    require(dynamicLights <= 1, "The custom UI duplicated dynamic lights");
                    int appended = nativeDynamicLights ? 0 : dynamicLights;
                    require(options().size() - appended == ids.size(), "The custom UI dropped native renderer options");
                    CHECKS.put("nativeOptionsRetained", ids.size());
                    CHECKS.put("nativeDynamicLights", nativeDynamicLights);
                    CHECKS.put("appendedDynamicLights", appended);
                } else {
                    require(options().size() >= 17, "The vanilla adapter dropped standard video settings");
                    CHECKS.put("nativeOptionsRetained", 16);
                }
                for (String key : List.of("graphics", "render_distance", "smooth_lighting", "max_framerate", "brightness", "gui_scale", "dynamic_fov")) option(key);
                if (Boolean.getBoolean("arcadium.videoImportedScaleQa")) {
                    require(Integer.valueOf(4).equals(invoke(option("gui_scale"), "getValue")), "The vanilla UI lost an imported 4x GUI scale");
                    CHECKS.put("importedGuiScale", true);
                }
                Object left = invoke(textNode(label("graphics"), false), "getBoundsInRoot");
                Object right = invoke(textNode(label("render_distance"), false), "getBoundsInRoot");
                require(((Number) invoke(left, "getLeft")).floatValue() < ((Number) invoke(right, "getLeft")).floatValue(), "Graphics and render distance are in the wrong columns");
                verifySettingRows();
                progress("brightness", 75);
                Object fov = option("dynamic_fov");
                dynamicFovValue = invoke(fov, "getValue");
                String control = invoke(fov, "getControl").toString();
                CHECKS.put("dynamicFovControl", control);
                if (control.equals("SLIDER")) {
                    int minimum = ((Number) invoke(fov, "getMinimum")).intValue();
                    int maximum = ((Number) invoke(fov, "getMaximum")).intValue();
                    int current = ((Number) dynamicFovValue).intValue();
                    require(minimum < maximum, "The native dynamic FOV slider has no range");
                    progress("dynamic_fov", current == minimum ? maximum : minimum);
                } else {
                    require(control.equals("TOGGLE"), "Unsupported dynamic FOV probe control: " + control);
                    click(label("dynamic_fov"));
                }
            }
            case 2 -> {
                require(Boolean.TRUE.equals(invoke(model, "getChanged")), "Editing did not mark the settings as changed");
                require(mc.options.gamma == gamma, "A staged slider modified native options before saving");
                require(!java.util.Objects.equals(dynamicFovValue, invoke(option("dynamic_fov"), "getValue")),
                    "The dynamic FOV control did not stage its change");
                click("Undo changes");
            }
            case 3 -> {
                require(!Boolean.TRUE.equals(invoke(model, "getChanged")), "Undo left unsaved changes behind");
                require(java.util.Objects.equals(dynamicFovValue, invoke(option("dynamic_fov"), "getValue")),
                    "Undo did not restore the dynamic FOV native binding");
                CHECKS.put("dynamicFovStagedAndUndo", true);
                @SuppressWarnings("unchecked")
                List<Object> choices = (List<Object>) invoke(option("graphics"), "getChoices");
                Object current = invoke(option("graphics"), "getValue");
                require(choices.size() == 2, "The graphics probe expects the two Minecraft 1.8.9 modes");
                graphicsChoice = choices.stream().filter(choice -> {
                    try { return !current.equals(invoke(choice, "getValue")); } catch (Exception failure) { throw new RuntimeException(failure); }
                }).findFirst().orElseThrow();
                click(label("graphics"));
            }
            case 4 -> click((String) invoke(graphicsChoice, "getLabel"));
            case 5 -> {
                require(mc.options.fancyGraphics == fancy, "A staged graphics selection modified native options before saving");
                progress("brightness", 75);
                click("Done");
            }
            case 6 -> {
                require(mc.screen == parent, "Done did not return to the parent screen");
                require(mc.options.fancyGraphics != fancy && Math.abs(mc.options.gamma - .75f) < .001f, "Done did not apply the native graphics and brightness bindings");
                require(Files.readString(mc.gameDir.toPath().resolve("options.txt")).contains("fancyGraphics:" + mc.options.fancyGraphics), "Native graphics were not saved to options.txt");
                mc.openScreen(new VideoOptionsScreen(parent, mc.options)); screen = mc.screen; model = field(screen, "model");
                CHECKS.put("nativeBindingsAndSave", true);
            }
            case 7 -> {
                if (pageIndex < PAGES.length) {
                    if (substage == 0) { click(PAGES[pageIndex] + "..."); substage = 1; return again(); }
                    if (substage == 1) {
                        require(hasText("Video settings / " + PAGES[pageIndex]), "A video category did not open");
                        capture(mc, "video-settings-" + PAGES[pageIndex].toLowerCase() + ".png");
                        setText(0, "arcadium-no-setting-should-match-78231"); substage = 2; return again();
                    }
                    if (substage == 2) {
                        require(hasText("No matching settings."), "Video settings search did not filter the category");
                        setText(0, ""); substage = 3; return again();
                    }
                    click("Done"); pageIndex++; substage = 0; return again();
                }
                if ("SLIDER".equals(invoke(option("gui_scale"), "getControl").toString())) progress("gui_scale", 1);
                else {
                    if (substage == 0) { click(label("gui_scale")); substage = 1; return again(); }
                    if (substage == 1) {
                        for (Object choice : (List<?>) invoke(option("gui_scale"), "getChoices")) {
                            if (Integer.valueOf(1).equals(invoke(choice, "getValue"))) { click((String) invoke(choice, "getLabel")); break; }
                        }
                        substage = 2; return again();
                    }
                }
                progress("render_distance", 6); click("Done");
            }
            case 8 -> {
                require(mc.options.guiScale == 1 && mc.options.viewDistance == 6, "GUI scale or render distance did not reach the native settings");
                if (FabricLoader.getInstance().isModLoaded("argentum")) {
                    Class<?> nativeType = Class.forName("dev.rdh.argentum.impl.gui.VideoOptionsScreen");
                    mc.openScreen((Screen) nativeType.getConstructor(Screen.class).newInstance(parent));
                } else mc.openScreen(new VideoOptionsScreen(parent, mc.options));
                screen = mc.screen; model = field(screen, "model");
                require(screen.getClass().getSimpleName().equals("ArcadiumVideoSettingsScreen"), "The renderer's own entry point bypassed Arcadium");
            }
            case 9 -> Display.setDisplayMode(new DisplayMode(854, 480));
            case 10 -> {
                if (mc.width != 854 || mc.height != 480) return again();
                verifyBounds(mc, List.of("Video settings", label("graphics"), label("render_distance"), "Details...", "Quality...", "Display...", "Other...", "Done"));
                verifySettingRows();
                capture(mc, "video-settings-small.png");
                Display.setDisplayMode(new DisplayMode(width, height));
                CHECKS.put("smallWindow", true);
            }
            case 11 -> {
                if (mc.width != width || mc.height != height) return again();
                CHECKS.put("categoriesAndSearch", PAGES.length);
                Files.createDirectories(stateFile(mc).getParent());
                Files.writeString(stateFile(mc), GSON.toJson(Map.of("fancyGraphics", mc.options.fancyGraphics, "gamma", mc.options.gamma,
                    "guiScale", mc.options.guiScale, "viewDistance", mc.options.viewDistance)));
                click("Done");
            }
            case 12 -> {
                require(mc.screen == parent, "Closing after a resize did not return to the parent");
                if (MENU_ONLY) {
                    require(mc.world == null && mc.player == null, "Menu-only video QA unexpectedly created a world");
                    CHECKS.put("noWorldCreated", true);
                    CHECKS.put("completed", true);
                    return true;
                }
                mc.startGame("arcadium-video-settings-qa", "Arcadium Video Settings QA",
                    new WorldSettings(71, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
            }
            case 13 -> {
                if (mc.world == null || mc.player == null) return again();
                worldParent = new GameMenuScreen();
                mc.openScreen(new VideoOptionsScreen(worldParent, mc.options));
                screen = mc.screen; model = field(screen, "model");
                require(screen.getClass().getSimpleName().equals("ArcadiumVideoSettingsScreen"), "The in-world entry point bypassed Arcadium");
            }
            case 14 -> {
                capture(mc, "video-settings-world.png");
                Object current = invoke(option("graphics"), "getValue");
                graphicsChoice = null;
                for (Object choice : (List<?>) invoke(option("graphics"), "getChoices")) {
                    if (!current.equals(invoke(choice, "getValue"))) { graphicsChoice = choice; break; }
                }
                require(graphicsChoice != null, "The in-world graphics selector has no alternate mode");
                click(label("graphics"));
            }
            case 15 -> click((String) invoke(graphicsChoice, "getLabel"));
            case 16 -> { progress("render_distance", 8); click("Done"); }
            case 17 -> {
                require(mc.screen == worldParent && mc.options.fancyGraphics == fancy && mc.options.viewDistance == 8,
                    "Applying graphics in-world did not rebuild the renderer and return to the pause menu");
                Files.writeString(stateFile(mc), GSON.toJson(Map.of("fancyGraphics", mc.options.fancyGraphics, "gamma", mc.options.gamma,
                    "guiScale", mc.options.guiScale, "viewDistance", mc.options.viewDistance)));
                CHECKS.put("inWorldApply", true);
                CHECKS.put("completed", true);
                return true;
            }
            default -> throw new IllegalStateException("Unexpected video probe stage: " + stage);
        }
        return next();
    }

    private static boolean next() { stage++; ticks = 0; return false; }
    private static boolean again() { ticks = 0; return false; }
    private static Path stateFile(Minecraft mc) { return mc.gameDir.toPath().resolve("forge-qa/video-saved-state.json"); }
    @SuppressWarnings("unchecked")
    private static List<Object> options() throws Exception { return (List<Object>) invoke(model, "getOptions"); }
    private static Object option(String key) throws Exception {
        for (Object value : options()) if (key.equals(invoke(value, "getKey"))) return value;
        throw new IllegalStateException("Missing video setting: " + key);
    }
    private static String label(String key) throws Exception {
        return (String) invoke(option(key), "getLabel");
    }

    private static void menuProbe(Minecraft mc) throws Exception {
        useScreen(mc.screen);
        switch (menuStage) {
            case 0 -> {
                require(mc.screen.getClass().getSimpleName().equals("ArcadiumMainMenu"), "The chrome probe did not start on the main menu");
                require(Boolean.TRUE.equals(invoke(mc.screen, "getShadesPanorama")), "The main menu does not match the Options panorama shading");
                menuWidth = mc.width; menuHeight = mc.height; menuFullscreen = mc.isFullscreen();
                Object account = invoke(described("Account menu"), "getBoundsInRoot");
                Object skin = invoke(textNode("Skins", true), "getBoundsInRoot");
                require(number(skin, "getTop") > number(account, "getBottom"), "Skins is not below the account menu");
                require(number(skin, "getLeft") == number(account, "getLeft"), "Skins is not aligned with the account menu");
                Object close = invoke(described("Quit game"), "getBoundsInRoot");
                Object fullscreen = invoke(described(menuFullscreen ? "Exit fullscreen" : "Enter fullscreen"), "getBoundsInRoot");
                require(number(close, "getRight") > mc.width - 50 && number(close, "getTop") < 40,
                        "Quit is not in the upper-right corner");
                require(number(fullscreen, "getRight") < number(close, "getLeft") && number(fullscreen, "getTop") == number(close, "getTop"),
                        "Fullscreen is not beside Quit");
                capture(mc, "video-settings-menu-controls.png");
                clickDescription("Account menu");
            }
            case 1 -> {
                require("Expanded".equals(properties(described("Account menu")).get("StateDescription")), "The account menu did not expand");
                require(hasText("Add account"), "The account menu has no add-account action");
                capture(mc, "video-settings-account-expanded.png");
                clickDescription("Account menu");
            }
            case 2 -> {
                require("Collapsed".equals(properties(described("Account menu")).get("StateDescription")), "The account menu did not collapse");
                click("Skins");
            }
            case 3 -> {
                require(mc.screen.getClass().getSimpleName().equals("SkinChangerScreen"), "Skins did not open the skin manager");
                click("Done");
            }
            case 4 -> {
                require(mc.screen.getClass().getSimpleName().equals("ArcadiumMainMenu"), "The skin manager did not return to the main menu");
                clickDescription(menuFullscreen ? "Exit fullscreen" : "Enter fullscreen");
            }
            case 5 -> {
                require(mc.isFullscreen() != menuFullscreen, "The fullscreen action did not change window mode");
                clickDescription(menuFullscreen ? "Enter fullscreen" : "Exit fullscreen");
            }
            case 6 -> {
                if (mc.width != menuWidth || mc.height != menuHeight) return;
                require(mc.isFullscreen() == menuFullscreen, "The fullscreen action did not restore window mode");
                CHECKS.put("mainMenuChromeAndSkins", true);
                CHECKS.put("fullscreenButtonRoundtrip", true);
                CHECKS.put("mainMenuOptionsPanoramaShade", true);
                Display.setDisplayMode(new DisplayMode(1920, 1080));
            }
            case 7 -> {
                if (mc.width != 1920 || mc.height != 1080) return;
                verifyMenuSize(mc, 480, 50);
                require(((Number) invoke(mc.screen, "getPanoramaBlurRadius")).floatValue() == 8f,
                        "The main menu does not match the Options panorama blur");
                capture(mc, "main-menu-1080p.png");
            }
            case 8 -> Display.setDisplayMode(new DisplayMode(854, 480));
            case 9 -> {
                if (mc.width != 854 || mc.height != 480) return;
                verifyMenuSize(mc, 292, 31);
                capture(mc, "main-menu-small.png");
            }
            case 10 -> Display.setDisplayMode(new DisplayMode(menuWidth, menuHeight));
            case 11 -> {
                if (mc.width != menuWidth || mc.height != menuHeight) return;
                CHECKS.put("responsiveMainMenu", true);
                CHECKS.put("softPanoramaBackdrop", true);
                menuVerified = true;
            }
        }
        menuStage++;
    }

    private static void verifyMenuSize(Minecraft mc, double width, double height) throws Exception {
        Object bounds = invoke(textNode("Singleplayer", true), "getBoundsInRoot");
        require(Math.abs(number(bounds, "getWidth") - width) <= 2, "The main menu width does not match its reference size");
        require(Math.abs(number(bounds, "getHeight") - height) <= 2, "The main menu height does not match its reference size");
        require(Math.abs((number(bounds, "getLeft") + number(bounds, "getRight")) / 2 - mc.width / 2.) <= 2,
                "The main menu buttons are not centered");
        verifyBounds(mc, List.of("Singleplayer", "Multiplayer", "Options", "Arcadium Options", "Skins"));
    }

    private static Object described(String label) throws Exception {
        for (Object node : allNodes()) {
            var values = properties(node);
            if (values.get("ContentDescription") instanceof Collection<?> labels && labels.contains(label) && values.containsKey("OnClick")) return node;
        }
        throw new IllegalStateException("Menu action was not found: " + label);
    }

    private static void clickDescription(String label) throws Exception {
        Object action = invoke(properties(described(label)).get("OnClick"), "getAction");
        require(Boolean.TRUE.equals(invoke(action, "invoke")), "The menu action was rejected: " + label);
    }

    private static double number(Object target, String method) throws Exception {
        return ((Number) invoke(target, method)).doubleValue();
    }

    private static Object control(String key) throws Exception {
        String caption = label(key);
        for (Object node : allNodes()) {
            var values = properties(node);
            if (values.get("ContentDescription") instanceof Collection<?> labels && labels.contains(caption)
                    && (values.containsKey("OnClick") || values.containsKey("SetProgress"))) return node;
        }
        return textNode(caption, true);
    }

    private static void verifySettingRows() throws Exception {
        for (String[] pair : List.of(new String[]{"graphics", "render_distance"}, new String[]{"smooth_lighting", "max_framerate"},
                new String[]{"gui_scale", "entity_shadows"}, new String[]{"brightness", "view_bobbing"}, new String[]{"dynamic_lights", "dynamic_fov"})) {
            Object first = invoke(control(pair[0]), "getBoundsInRoot"), second = invoke(control(pair[1]), "getBoundsInRoot");
            require(Math.abs(number(first, "getHeight") - number(second, "getHeight")) < 1, "Video setting controls have different heights");
            require(Math.abs(number(first, "getTop") - number(second, "getTop")) < 1, "Video setting controls are not aligned");
        }
        CHECKS.put("consistentControlRows", true);
    }
    private static List<Map<String, Object>> semanticSummary() throws Exception {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        for (Object node : allNodes()) {
            var properties = properties(node);
            if (!properties.containsKey("Text") && !properties.containsKey("ContentDescription")) continue;
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            if (properties.get("Text") instanceof Collection<?> values) {
                ArrayList<String> labels = new ArrayList<>();
                for (Object value : values) labels.add(String.valueOf(invoke(value, "getText")));
                entry.put("text", labels);
            }
            if (properties.containsKey("ContentDescription")) entry.put("description", properties.get("ContentDescription"));
            entry.put("clickable", properties.containsKey("OnClick"));
            entry.put("slider", properties.containsKey("SetProgress"));
            entry.put("bounds", String.valueOf(invoke(node, "getBoundsInRoot")));
            result.add(entry);
            if (result.size() >= 256) break;
        }
        return result;
    }
    private static void progress(String key, float value) throws Exception {
        String label = label(key);
        for (Object node : allNodes()) {
            var properties = properties(node);
            if (properties.get("ContentDescription") instanceof Collection<?> names && names.contains(label) && properties.containsKey("SetProgress")) {
                Object action = invoke(properties.get("SetProgress"), "getAction");
                require(Boolean.TRUE.equals(invoke(action, "invoke", value)), "The video slider rejected a change: " + label);
                return;
            }
        }
        throw new IllegalStateException("Video slider semantics are missing: " + label);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message + " (video stage " + stage + ")"); }
}
