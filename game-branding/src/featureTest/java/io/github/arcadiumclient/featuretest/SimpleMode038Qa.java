// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;

import com.google.gson.JsonParser;
import io.github.arcadiumclient.featuretest.mixin.ScreenProbeAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

public final class SimpleMode038Qa {
    private static final String MOD_ID = "arcadium-no-render.json";
    private static final String SHELL = "org.polyfrost.oneconfig.internal.ui.shell.ShellState";
    private static final String GRAPH = "org.polyfrost.oneconfig.internal.ui.navigation.graph.";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static Object preferences, mode, module, enabled, shell, active;
    private static Object originalMode, originalEnabled, originalCategory;
    private static String originalQuery;
    private static Screen parent;
    private static int stage, ticks, width, height, mouseX, mouseY;
    private static boolean begun, done, originalFavorite, originalHidden, originalFocus;

    private SimpleMode038Qa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        try { return run(mc, checks); }
        catch (Exception failure) {
            try { restore(mc); } catch (Exception restoreFailure) { failure.addSuppressed(restoreFailure); }
            throw failure;
        }
    }

    private static boolean run(Minecraft mc, Map<String, Object> checks) throws Exception {
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
            "Simple mode QA requires the isolated offline session");
        if (!begun) {
            checks.put("simpleMode038", RESULT);
            parent = mc.screen; width = mc.width; height = mc.height;
            mouseX = Mouse.getX(); mouseY = Mouse.getY();
            active = stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active");
            Path profile = ((Path) call(active, "getFolder")).toAbsolutePath().normalize();
            require(profile.startsWith(mc.gameDir.toPath().toRealPath()), "Simple mode QA must not use a real client profile");
            Object registry = object("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry");
            preferences = call(registry, "findTree", "oneconfig.json");
            module = call(registry, "findTree", MOD_ID);
            require(preferences != null && module != null, "Simple mode QA configs were not registered");
            mode = call(preferences, "getProp", "guiMode");
            enabled = call(module, "getProp", "enabled");
            require(mode != null && enabled != null, "Simple mode controls were not registered");
            Object defaultMode = call(mode, "getMetadata", "default");
            require(defaultMode instanceof Number && ((Number) defaultMode).intValue() == 0,
                "Modern is no longer the default GUI mode");
            shell = object(SHELL);
            originalMode = call(mode, "get"); originalEnabled = call(enabled, "get");
            originalQuery = (String) call(shell, "getSearchQuery");
            originalFocus = (boolean) field(shell, "searchFieldFocused");
            @SuppressWarnings("unchecked") Map<String, String> categories = (Map<String, String>) call(shell, "getSelectedCategories");
            originalCategory = categories.remove("oneconfig.json");
            originalFavorite = favorite(); originalHidden = hidden();
            begun = true;
            setMode(0); call(enabled, "setAsReferential", false);
            if (!originalFavorite) call(object("org.polyfrost.oneconfig.internal.ui.api.ModFavorites"), "toggle", MOD_ID);
            if (originalHidden) call(object("org.polyfrost.oneconfig.internal.ui.api.ModHidden"), "toggle", MOD_ID);
            call(shell, "setSearchQuery", "");
            Display.setDisplayMode(new DisplayMode(1600, 900));
            open(mc, "PreferencesRoute");
        }
        require(++ticks < 180, "Simple mode QA timed out at stage " + stage);
        if (ticks < 20) return false;
        PlayMenusQa.useScreen(mc.screen);
        switch (stage) {
            case 0 -> {
                require(hasText(mc.screen, "GUI Mode") && hasText(mc.screen, "Modern"), "Preferences did not show Modern GUI Mode");
                PlayMenusQa.click("Modern");
            }
            case 1 -> PlayMenusQa.click("Simple");
            case 2 -> {
                require(simple() && ((Number) call(mode, "get")).intValue() == 1, "Choosing Simple did not change the live mode");
                capture(mc, "simple-mode-038-preferences.png");
                require(Boolean.TRUE.equals(call(active, "save", preferences)), "Simple GUI mode could not be saved");
                Path file = ((Path) call(active, "getFolder")).resolve("oneconfig.json");
                var json = new JsonParser().parse(Files.readString(file)).getAsJsonObject();
                require(json.get("guiMode").getAsInt() == 1, "Simple GUI mode was not persisted");
                open(mc, "ModsRoute");
            }
            case 3 -> {
                require(simple() && "Mods".equals(title()), "The Simple mod grid did not open");
                Object toggle = described("Toggle NoRender");
                require(PlayMenusQa.properties(toggle).containsKey("ToggleableState"), "The module control is not a real switch");
                var columns = new HashSet<Integer>();
                for (Object node : PlayMenusQa.allNodes()) {
                    Object descriptions = PlayMenusQa.properties(node).get("ContentDescription");
                    if (descriptions instanceof Collection<?> values && values.stream().anyMatch(value -> value.toString().startsWith("Open ") && value.toString().endsWith(" settings"))) {
                        Object rect = call(node, "getBoundsInRoot");
                        columns.add(Math.round(((Number) call(rect, "getLeft")).floatValue()));
                    }
                }
                require(columns.size() == 3, "Simple mode did not use its three-column layout: " + columns);
                RESULT.put("simpleColumns", columns.size());
                capture(mc, "simple-mode-038-mods.png");
                pointerClick(mc, toggle, 1, false);
            }
            case 4 -> {
                require(!enabled() && "Mods".equals(title()), "Right-click changed a Simple module switch or opened settings");
                pointerClick(mc, described("Toggle NoRender"), 0, false);
            }
            case 5 -> {
                require(enabled() && "Mods".equals(title()), "The module switch did not toggle without opening settings");
                require("".equals(call(shell, "getSearchQuery")), "The grid switch changed the search query");
                RESULT.put("gridSwitchClickIsSeparate", true);
                pointerClick(mc, described("Open NoRender settings"), 1, true);
            }
            case 6 -> {
                require(enabled() && "Mods".equals(title()), "Right-click on a Simple card had an assigned action");
                RESULT.put("rightClickUnassigned", true);
                pointerClick(mc, described("Open NoRender settings"), 0, true);
            }
            case 7 -> {
                require("NoRender".equals(title()) && hasText(mc.screen, "Enabled"), "Clicking the card body did not open the full module settings");
                RESULT.put("cardBodyOpensFullSettings", true);
                back();
            }
            case 8 -> {
                require("Mods".equals(title()), "Back did not return to the Simple grid");
                setSearch("NoRender");
            }
            case 9 -> {
                require("NoRender".equals(call(shell, "getSearchQuery")), "The real search field did not update");
                Object toggle = described("Toggle NoRender");
                capture(mc, "simple-mode-038-search.png");
                pointerClick(mc, toggle, 0, false);
            }
            case 10 -> {
                require(!enabled() && "Mods".equals(title()), "The Search result switch did not toggle in place");
                require("NoRender".equals(call(shell, "getSearchQuery")), "The Search result switch discarded the query");
                RESULT.put("searchSwitchPreservesQuery", true);
                pointerClick(mc, described("Open NoRender settings"), 0, true);
            }
            case 11 -> {
                require("NoRender".equals(title()) && hasText(mc.screen, "Enabled"), "Search opened a partial module configuration");
                require("".equals(call(shell, "getSearchQuery")), "Opening module settings retained the global search filter");
                back();
            }
            case 12 -> {
                RESULT.put("searchBackTitle", title());
                RESULT.put("searchBackQuery", call(shell, "getSearchQuery"));
                require("NoRender".equals(call(shell, "getSearchQuery")), "Back did not restore the prior Search query: " + call(shell, "getSearchQuery"));
                require("Mods".equals(title()), "Search results kept the previous module's page title: " + title());
                described("Toggle NoRender");
                RESULT.put("searchNavigationRoundtrip", true);
                open(mc, "PreferencesRoute");
            }
            case 13 -> PlayMenusQa.click("Simple");
            case 14 -> PlayMenusQa.click("Modern");
            case 15 -> {
                require(!simple() && ((Number) call(mode, "get")).intValue() == 0, "Choosing Modern did not restore the original mode");
                open(mc, "ModsRoute");
            }
            case 16 -> {
                require(!simple() && "Mods".equals(title()), "Modern mode did not reopen");
                for (Object node : PlayMenusQa.allNodes()) {
                    Object value = PlayMenusQa.properties(node).get("ContentDescription");
                    require(!(value instanceof Collection<?> labels && labels.contains("Toggle NoRender")), "A Simple card control remained in Modern mode");
                }
                capture(mc, "simple-mode-038-modern.png");
                RESULT.put("modernDefaultAndRoundtrip", true);
                RESULT.put("preferencePersisted", true);
                restore(mc);
            }
            case 17 -> {
                if (mc.width != width || mc.height != height) return false;
                require(originalMode.equals(call(mode, "get")) && originalEnabled.equals(call(enabled, "get")), "Simple QA did not restore the original module settings");
                RESULT.put("settingsRestored", true);
                RESULT.put("completed", true);
                done = true;
                return true;
            }
            default -> throw new IllegalStateException("Unknown Simple mode QA stage");
        }
        stage++; ticks = 0;
        return false;
    }

    private static Object described(String label) throws Exception {
        for (Object node : PlayMenusQa.allNodes()) {
            var values = PlayMenusQa.properties(node);
            if (values.get("ContentDescription") instanceof Collection<?> descriptions && descriptions.contains(label) && values.containsKey("OnClick")) return node;
        }
        throw new IllegalStateException("Simple GUI action was not found at stage " + stage + ": " + label);
    }

    private static void pointerClick(Minecraft mc, Object node, int button, boolean body) throws Exception {
        Object rect = call(node, "getBoundsInRoot");
        double left = ((Number) call(rect, "getLeft")).doubleValue(), right = ((Number) call(rect, "getRight")).doubleValue();
        double top = ((Number) call(rect, "getTop")).doubleValue(), bottom = ((Number) call(rect, "getBottom")).doubleValue();
        require(right > left && bottom > top, "The Simple GUI pointer target has no area");
        double x = (left + right) / 2., y = body ? top + Math.min(22., (bottom - top) / 4.) : (top + bottom) / 2.;
        Object platform = stat("org.polyfrost.oneconfig.api.platform.v1.Platform", "screen");
        double ratio = ((Number) call(platform, "pixelRatio")).doubleValue();
        int viewport = ((Number) call(platform, "viewportHeight")).intValue();
        Mouse.setCursorPosition((int) Math.round(x * ratio), viewport - (int) Math.round(y * ratio) - 1);
        Screen screen = mc.screen;
        int sx = (int) Math.round(x * screen.width / (mc.width / ratio));
        int sy = (int) Math.round(y * screen.height / (mc.height / ratio));
        ((ScreenProbeAccess) screen).arcadium$click(sx, sy, button);
        ((ScreenProbeAccess) screen).arcadium$release(sx, sy, button);
    }

    private static void setSearch(String text) throws Exception {
        for (Object node : PlayMenusQa.allNodes()) {
            Object setter = PlayMenusQa.properties(node).get("SetText");
            if (setter == null) continue;
            Object value = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class).newInstance(text, List.of(), List.of());
            require(Boolean.TRUE.equals(call(call(setter, "getAction"), "invoke", value)), "The search field rejected input");
            return;
        }
        throw new IllegalStateException("The Simple mode search input was not found");
    }

    private static void open(Minecraft mc, String route) throws Exception {
        mc.openScreen(parent);
        stat("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", object(GRAPH + route));
    }
    private static void back() throws Exception {
        Object nav = object("org.polyfrost.oneconfig.internal.ui.shell.LocalNavController");
        call(call(nav, "getWrapper"), "back");
    }
    private static boolean simple() throws Exception { return (boolean) call(shell, "getSimpleMode"); }
    private static boolean enabled() throws Exception { return Boolean.TRUE.equals(call(enabled, "get")); }
    private static String title() throws Exception { return (String) call(shell, "getTitle"); }
    private static boolean favorite() throws Exception { return (boolean) call(object("org.polyfrost.oneconfig.internal.ui.api.ModFavorites"), "isFavorite", MOD_ID); }
    private static boolean hidden() throws Exception { return (boolean) call(object("org.polyfrost.oneconfig.internal.ui.api.ModHidden"), "isHidden", MOD_ID); }
    private static void setMode(int value) throws Exception { call(mode, "setAsReferential", value); }
    private static void capture(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); }

    private static void restore(Minecraft mc) throws Exception {
        if (!begun) return;
        call(mode, "setAsReferential", originalMode);
        call(enabled, "setAsReferential", originalEnabled);
        call(active, "save", preferences); call(active, "save", module);
        if (favorite() != originalFavorite) call(object("org.polyfrost.oneconfig.internal.ui.api.ModFavorites"), "toggle", MOD_ID);
        if (hidden() != originalHidden) call(object("org.polyfrost.oneconfig.internal.ui.api.ModHidden"), "toggle", MOD_ID);
        @SuppressWarnings("unchecked") Map<String, String> categories = (Map<String, String>) call(shell, "getSelectedCategories");
        if (originalCategory == null) categories.remove("oneconfig.json");
        else categories.put("oneconfig.json", (String) originalCategory);
        mc.openScreen(parent);
        call(shell, "setSearchQuery", originalQuery);
        findField(shell.getClass(), "searchFieldFocused").setBoolean(shell, originalFocus);
        Display.setDisplayMode(new DisplayMode(width, height));
        Mouse.setCursorPosition(mouseX, mouseY);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
