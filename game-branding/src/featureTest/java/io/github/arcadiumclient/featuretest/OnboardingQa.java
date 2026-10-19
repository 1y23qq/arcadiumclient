package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.PlayMenusQa.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

final class OnboardingQa {
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static int stage, ticks, width, height;
    private static Class<?> preferences, sprint;
    private static final Map<String, Object> BLOCK_OPTIONS = new LinkedHashMap<>();

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Onboarding QA requires the isolated offline session");
        require(mc.world == null, "Onboarding QA must not enter a world");
        checks.put("onboarding", RESULT);
        if (++ticks < 14) return false;
        useScreen(mc.screen);
        switch (stage) {
            case 0 -> {
                width = mc.width; height = mc.height;
                preferences = Class.forName("io.github.arcadiumclient.runtime.client.ClientPreferences");
                sprint = Class.forName("io.github.arcadiumclient.runtime.client.features.ToggleSprintConfig");
                preferences.getMethod("setSetupComplete", boolean.class).invoke(null, false);
                Object config = preferences.getField("INSTANCE").get(null);
                invoke(config, "save");
                reopen(mc);
            }
            case 1 -> {
                require(hasText("Select a theme") && hasText("Dark") && hasText("Light") && hasText("Glass") && hasText("Minecraft"), "The separate color/style selectors are missing");
                capture(mc, "onboarding-01-theme.png");
                click("Light");
            }
            case 2 -> { require(selected("Light"), "Light did not select the light theme"); click("Minecraft"); }
            case 3 -> { require(selected("Minecraft"), "Minecraft style did not activate"); click("Next"); }
            case 4 -> { require(hasText("Toggle Sprint"), "The sprint page did not open"); click("Disabled"); }
            case 5 -> {
                require(Boolean.FALSE.equals(sprint.getMethod("getToggleSprint").invoke(null)), "The disabled choice did not update the real sprint setting");
                click("Enabled");
            }
            case 6 -> {
                require(Boolean.TRUE.equals(sprint.getMethod("getToggleSprint").invoke(null)), "The enabled choice did not update the real sprint setting");
                capture(mc, "onboarding-02-sprint.png");
                click("Back");
            }
            case 7 -> { require(selected("Light") && selected("Minecraft"), "Back lost the selected theme"); click("Dark"); }
            case 8 -> click("Glass");
            case 9 -> { require(selected("Dark") && selected("Glass"), "Glass Dark did not activate"); click("Next"); }
            case 10 -> click("Next");
            case 11 -> {
                require(hasText("Block Overlay"), "The block overlay page did not open");
                Object preview = described("Block Overlay preview");
                require("Texture loaded".equals(properties(preview).get("StateDescription")), "The block preview did not load the actual game texture");
                Object registry = Class.forName("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry").getField("INSTANCE").get(null);
                Object tree = invoke(registry, "findTree", "custom-block-highlight.json");
                ArrayList<Map<String, Object>> settings = new ArrayList<>();
                settings(tree, settings);
                RESULT.put("settings", settings);
                require(settings.size() >= 10, "The native block overlay options were not retained");
                capture(mc, "onboarding-03-block-overlay.png");
            }
            case 12 -> Display.setDisplayMode(new DisplayMode(854, 480));
            case 13 -> {
                if (mc.width != 854 || mc.height != 480) return false;
                verifyBounds(mc, List.of("Block Overlay", "Back", "Next"));
                capture(mc, "onboarding-03-block-overlay-small.png");
                clickDescription("Enabled: Off");
            }
            case 14 -> {
                require(Boolean.FALSE.equals(blockValue("globalModToggle")), "The setup master switch did not update native rendering");
                clickDescription("Enabled: On");
                clickDescription("Outline: White");
                progress("Opacity", 200f);
                progress("Width", 3.5f);
                clickDescription("See-through: Off");
                clickDescription("Fill: Off");
                clickDescription("Fill colour: Cyan");
                progress("Fill opacity", 128f);
                clickDescription("Fill rainbow: On");
                clickDescription("Animations: Off");
            }
            case 15 -> {
                require(Boolean.TRUE.equals(blockValue("globalModToggle")), "The setup master switch did not restore rendering");
                require(java.awt.Color.WHITE.equals(blockValue("lineCol")) && java.awt.Color.WHITE.equals(blockValue("lineCol2")), "The outline palette did not change the actual game colors");
                require(((Number) blockValue("lineAlpha")).intValue() == 200 && ((Number) blockValue("lineWidth")).floatValue() == 3.5f, "The outline sliders did not change the native values");
                require("NORMAL".equals(String.valueOf(blockValue("lineDepthTest"))), "The depth option did not change the native mode");
                require(Boolean.FALSE.equals(blockValue("fillEnabled")) && Boolean.TRUE.equals(blockValue("fillRainbow")), "The fill switches are not bound to the actual module");
                require(new java.awt.Color(0x58D5ED).equals(blockValue("fillCol")) && ((Number) blockValue("fillAlpha")).intValue() == 128, "The fill controls did not update the native colors and opacity");
                require(Boolean.FALSE.equals(blockValue("doEasing")), "The animations setting did not change the native easing option");
                Display.setDisplayMode(new DisplayMode(width, height));
            }
            case 16 -> { if (mc.width != width || mc.height != height) return false; click("Next"); }
            case 17 -> { require(hasText("All Done!"), "The completion page did not open"); capture(mc, "onboarding-04-complete.png"); click("Back"); }
            case 18 -> { require(hasText("Block Overlay"), "Back did not return to Block Overlay"); click("Next"); }
            case 19 -> click("Finish");
            case 20 -> {
                require(Boolean.TRUE.equals(preferences.getMethod("getSetupComplete").invoke(null)) && hasText("Singleplayer"), "Finish did not persist completion and show the main menu");
                reopen(mc);
            }
            case 21 -> {
                require(hasText("Singleplayer") && !hasText("Select a theme"), "The tutorial opened again after completion");
                sprint.getMethod("setToggleSprint", boolean.class).invoke(null, false);
                preferences.getMethod("setSetupComplete", boolean.class).invoke(null, false);
                reopen(mc);
            }
            case 22 -> click("Skip");
            case 23 -> {
                require(hasText("Singleplayer") && Boolean.TRUE.equals(preferences.getMethod("getSetupComplete").invoke(null)), "Skip did not complete the tutorial");
                require(Boolean.FALSE.equals(sprint.getMethod("getToggleSprint").invoke(null)), "Skip overwrote an existing sprint setting");
                sprint.getMethod("setToggleSprint", boolean.class).invoke(null, true);
                RESULT.put("colorAndStyleSelection", true);
                RESULT.put("sprintBindings", true);
                RESULT.put("blockPreviewTexture", true);
                RESULT.put("nativeBlockControls", true);
                RESULT.put("smallWindow", true);
                RESULT.put("backNavigation", true);
                RESULT.put("completionPersistence", true);
                RESULT.put("skipPreservesSettings", true);
                RESULT.put("completed", true);
                return true;
            }
        }
        ticks = 0; stage++;
        return false;
    }

    private static void reopen(Minecraft mc) throws Exception {
        mc.openScreen((Screen) Class.forName("io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu").getConstructor().newInstance());
    }

    private static boolean selected(String label) throws Exception {
        return Boolean.TRUE.equals(properties(textNode(label, true)).get("Selected"));
    }

    private static Object described(String label) throws Exception {
        for (Object node : allNodes()) {
            if (properties(node).get("ContentDescription") instanceof Collection<?> values && values.contains(label)) return node;
        }
        throw new IllegalStateException("The preview node is missing");
    }

    private static void settings(Object tree, List<Map<String, Object>> output) throws Exception {
        Map<?, ?> nodes = (Map<?, ?>) tree.getClass().getField("map").get(tree);
        for (Map.Entry<?, ?> entry : nodes.entrySet()) {
            Object node = entry.getValue();
            if (node.getClass().getName().equals("org.polyfrost.oneconfig.api.config.v1.Tree")) settings(node, output);
            else {
                Object value = invoke(node, "get");
                BLOCK_OPTIONS.put(String.valueOf(entry.getKey()), node);
                output.add(Map.of("id", String.valueOf(entry.getKey()), "type", value == null ? "null" : value.getClass().getName(),
                    "value", value == null ? "null" : String.valueOf(value)));
            }
        }
    }

    private static Object blockValue(String id) throws Exception { return invoke(BLOCK_OPTIONS.get(id), "get"); }

    private static void clickDescription(String label) throws Exception {
        Object action = invoke(properties(described(label)).get("OnClick"), "getAction");
        require(Boolean.TRUE.equals(invoke(action, "invoke")), "The setup action was rejected: " + label);
    }

    private static void progress(String label, float value) throws Exception {
        Object action = invoke(properties(described("Block Overlay " + label)).get("SetProgress"), "getAction");
        require(Boolean.TRUE.equals(invoke(action, "invoke", value)), "The setup slider rejected a value: " + label);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message + " (onboarding stage " + stage + ")");
    }
}
