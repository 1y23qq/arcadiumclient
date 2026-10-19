package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.field;
import static io.github.arcadiumclient.featuretest.Visual033Qa.nodes;
import static io.github.arcadiumclient.featuretest.Visual033Qa.object;
import static io.github.arcadiumclient.featuretest.Visual033Qa.properties;
import static io.github.arcadiumclient.featuretest.Visual033Qa.semanticsRoot;
import static io.github.arcadiumclient.featuretest.Visual033Qa.stat;
import static io.github.arcadiumclient.featuretest.Visual033Qa.staticField;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Mouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.options.ServerList;
import net.minecraft.client.options.ServerListEntry;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

public final class Root035Qa {
    private static final String HEIGHT = "io.github.arcadiumclient.runtime.client.features.HeightLimitHud";
    private static final String SOURCE = "io.github.arcadiumclient.runtime.client.features.HeightLimitSource";
    private static final String MANAGER = "org.polyfrost.oneconfig.api.hud.v1.HudManager";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<Field, Object> SOURCE_STATE = new LinkedHashMap<>();
    private static final List<Map<String, Object>> HEIGHT_CASES = new ArrayList<>();
    private static Object manager, hud, preferences, preference;
    private static Screen parent, servers;
    private static Path serverFile, oldServerFile;
    private static byte[] serverBytes, oldServerBytes;
    private static double x, y, z;
    private static float yaw, pitch;
    private static boolean flying, begun, done, fullscreen, grabbedBeforeToggle, continuouslyFocused;
    private static int phase, ticks, width, height, savedStart;
    private static double previousScroll;

    private Root035Qa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!Boolean.getBoolean("arcadium.release035Qa") || done) return true;
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken())
                && mc.isSingleplayer() && !mc.getServer().isPublished(), "Root QA requires the isolated offline world");
        if (!begun) begin(mc, checks);
        if (phase > 10) continuouslyFocused &= Display.isActive();
        require(++ticks < 160, "Root 0.3.5 QA timed out at phase " + phase);
        if (ticks < 18) return false;
        switch (phase) {
            case 0 -> {
                require("67 / 256".equals(call(hud, "getText")), "Classic Height Limit format changed");
                require("HIDDEN".equals(call(call(call(hud, "getTree"), "getProp", "showCurrentY"), "getDisplay").toString()),
                        "Compact-only controls were visible in Classic style");
                setHud("style", 1);
                require("256 (189)".equals(call(hud, "getText")), "Compact Height Limit did not match the reference");
                for (String key : List.of("showYLimit", "showCurrentY", "showDistanceToLimit"))
                    require("SHOWN".equals(call(call(call(hud, "getTree"), "getProp", key), "getDisplay").toString()), "Compact control is hidden: " + key);
            }
            case 1 -> {
                String[] expected = {"", "256", "67", "67 / 256", "(189)", "256 (189)", "67 (189)", "67 / 256 (189)"};
                for (int flags = 0; flags < 8; flags++) {
                    setHud("showYLimit", (flags & 1) != 0);
                    setHud("showCurrentY", (flags & 2) != 0);
                    setHud("showDistanceToLimit", (flags & 4) != 0);
                    require(expected[flags].equals(call(hud, "getText")), "A Compact visibility combination is incorrect");
                    require(Boolean.valueOf(flags != 0).equals(call(hud, "shouldShow")), "Empty Compact HUD was not hidden");
                    HEIGHT_CASES.add(Map.of("flags", flags, "text", expected[flags]));
                }
                setHud("showYLimit", true); setHud("showCurrentY", false); setHud("showDistanceToLimit", true);
                call(hud, "update"); stat(MANAGER, "invalidate");
            }
            case 2 -> {
                require(((Number) call(hud, "getRenderedW")).floatValue() > 0 && ((Number) call(hud, "getRenderedH")).floatValue() > 0,
                        "The Compact Height Limit HUD was not drawn");
                capture(mc, "root035-height-compact.png");
                call(hud, "save");
                RESULT.put("compactText", call(hud, "getText")); RESULT.put("heightCombinations", HEIGHT_CASES);
                prepareServers(mc);
            }
            case 3 -> {
                require(scroll() == 0 && visible("Scroll QA 00"), "Default server list position was not Top");
                capture(mc, "root035-server-top.png");
                mc.openScreen(parent); call(preference, "setAs", 1); call(preferences, "save");
            }
            case 4 -> {
                loadPreferences();
                require(start() == 1, "Bottom server-list preference did not survive persistence reload");
                mc.openScreen(servers);
            }
            case 5 -> {
                require(scroll() > 0 && visible("Scroll QA 39"), "Bottom preference did not open the actual last saved server");
                previousScroll = scroll();
                RESULT.put("bottomInitialScroll", previousScroll);
                capture(mc, "root035-server-bottom.png");
                resize(854, 480);
            }
            case 6 -> {
                if (mc.width != 854 || mc.height != 480) return false;
                require(mc.screen == servers && scroll() > 0 && !visible("Scroll QA 00"), "Resizing reset the active server viewport");
                RESULT.put("bottomAfterResize", scroll());
                mc.openScreen(parent); mc.openScreen(servers);
            }
            case 7 -> {
                require(scroll() > 0 && visible("Scroll QA 39"), "Reopening did not apply the Bottom preference");
                mc.openScreen(parent); call(preference, "setAs", 0); call(preferences, "save");
            }
            case 8 -> {
                loadPreferences();
                require(start() == 0, "Top preference did not survive persistence reload");
                mc.openScreen(servers);
            }
            case 9 -> {
                require(scroll() == 0 && visible("Scroll QA 00"), "Switching back to Top did not reset the viewport");
                RESULT.put("topBottomPersistenceAndReopen", true);
                restore(mc);
            }
            case 10 -> {
                if (mc.width != width || mc.height != height) return false;
                require(equal(serverFile, serverBytes) && equal(oldServerFile, oldServerBytes), "The saved server files were not restored");
                grabbedBeforeToggle = Mouse.isGrabbed(); continuouslyFocused = Display.isActive();
                mc.toggleFullscreen();
            }
            case 11 -> {
                require(mc.isFullscreen() != fullscreen && Display.isFullscreen() != fullscreen,
                        "Capture-compatible SDL window did not toggle fullscreen");
                require(mc.width > 0 && mc.height > 0, "Fullscreen produced an invalid framebuffer size");
                capture(mc, "root035-fullscreen-toggle.png");
                mc.toggleFullscreen();
            }
            case 12 -> {
                if (mc.width != width || mc.height != height) return false;
                require(mc.isFullscreen() == fullscreen && Display.isFullscreen() == fullscreen,
                        "Fullscreen did not restore its original state");
                boolean checkCapture = grabbedBeforeToggle && continuouslyFocused && mc.screen == null;
                require(!checkCapture || Mouse.isGrabbed(), "Returning from fullscreen lost relative mouse capture while focused");
                RESULT.put("relativeMouseCapture", Map.of("checked", checkCapture, "grabbedBefore", grabbedBeforeToggle,
                        "grabbedAfter", Mouse.isGrabbed(), "continuouslyFocused", continuouslyFocused));
                RESULT.put("fullscreenResizeAndMouseCapture", true);
                RESULT.put("fixtureRestored", true); RESULT.put("completed", true); done = true; return true;
            }
            default -> throw new IllegalStateException("Unknown root QA phase");
        }
        phase++; ticks = 0; return false;
    }

    @SuppressWarnings("unchecked")
    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        Path game = mc.gameDir.toPath().toRealPath();
        require(game.equals(Path.of(System.getProperty("arcadium.forgeQa.gameDir")).toRealPath()), "Root QA directory mismatch");
        begun = true; checks.put("root035", RESULT); parent = mc.screen;
        x = mc.player.x; y = mc.player.y; z = mc.player.z; yaw = mc.player.yaw; pitch = mc.player.pitch;
        flying = mc.player.abilities.flying; width = mc.width; height = mc.height; fullscreen = mc.isFullscreen();
        mc.player.abilities.flying = true; mc.player.setPositionAndAngles(x, 67, z, yaw, pitch);
        for (String name : List.of("value", "lastWorld", "sampledAt", "limitAt")) {
            Field field = staticField(SOURCE, name); SOURCE_STATE.put(field, field.get(null));
        }
        Object snapshot = Class.forName(SOURCE + "$Snapshot").getConstructor(boolean.class, String.class, Integer.class)
                .newInstance(true, "arcadium_qa_limit", 256);
        staticField(SOURCE, "value").set(null, snapshot); staticField(SOURCE, "lastWorld").set(null, mc.world);
        staticField(SOURCE, "sampledAt").setLong(null, System.nanoTime() + 3_600_000_000_000L);
        manager = object(MANAGER);
        Object provider = ((Collection<?>) call(manager, "providers")).stream().filter(value -> value.getClass().getName().equals(HEIGHT)).findFirst().orElseThrow();
        hud = call(provider, "make", (Object) null);
        ((List<Object>) call(manager, "getActiveInstances")).add(hud); call(hud, "setup");
        call(hud, "setHidden", false); call(hud, "setShowBackground", true); call(hud, "setShowShadow", true);
        call(hud, "setAbsolutePosition", 16f, 88f);
        require(((Number) call(hud, "getStyle")).intValue() == 0, "Height Limit style did not preserve its default");
        Object revision = call(manager, "getInstancesRevision");
        call(revision, "setIntValue", ((Number) call(revision, "getIntValue")).intValue() + 1);
        stat(MANAGER, "invalidate");
        preferences = staticField("org.polyfrost.oneconfig.internal.OneConfigConfig", "INSTANCE").get(null);
        preference = call(call(preferences, "getTree"), "getProp", "serverListStartPosition");
        require(preference != null, "The new server-list preference was not registered");
        savedStart = start(); require(savedStart == 0, "Server list did not default to Top in the fresh fixture");
    }

    private static void setHud(String id, Object value) throws Exception { call(call(call(hud, "getTree"), "getProp", id), "setAs", value); }
    private static int start() throws Exception { return ((Number) call(preference, "get")).intValue(); }
    private static void loadPreferences() throws Exception {
        Object active = stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active");
        require(Boolean.TRUE.equals(call(field(active, "backend"), "load", call(preferences, "getTree"))),
                "The preferences backend could not reload its saved data");
    }
    private static void prepareServers(Minecraft mc) throws Exception {
        serverFile = mc.gameDir.toPath().resolve("servers.dat"); oldServerFile = mc.gameDir.toPath().resolve("servers.dat_old");
        serverBytes = Files.exists(serverFile) ? Files.readAllBytes(serverFile) : null;
        oldServerBytes = Files.exists(oldServerFile) ? Files.readAllBytes(oldServerFile) : null;
        ServerList list = new ServerList(mc); while (list.size() > 0) list.remove(0);
        for (int i = 0; i < 40; i++) list.add(new ServerListEntry(String.format(java.util.Locale.ROOT, "Scroll QA %02d", i), "127.0.0.1:9", false));
        list.save(); staticField(Root033Qa.class.getName(), "networkIsolated").setBoolean(null, true);
        mc.openScreen(new MultiplayerScreen(parent)); servers = mc.screen;
        require(servers.getClass().getSimpleName().equals("ArcadiumServersScreen"), "The native server menu was not replaced");
    }
    private static double scroll() throws Exception {
        for (Object node : nodes(semanticsRoot(servers))) {
            Object axis = properties(node).get("VerticalScrollAxisRange");
            if (axis != null) return ((Number) call(call(axis, "getValue"), "invoke")).doubleValue();
        }
        throw new IllegalStateException("Server scroll semantics are missing");
    }
    private static boolean visible(String text) throws Exception { return Visual033Qa.hasText(servers, text); }
    private static void resize(int w, int h) throws Exception {
        Display.setDisplayMode(new DisplayMode(w, h));
        Object display = stat("pl.tomgirl.pylon.window.DisplaySdl", "instance");
        stat("org.lwjgl.sdl.SDLVideo", "SDL_SetWindowSize", call(display, "getHandle"), w, h);
    }
    private static void restore(Minecraft mc) throws Exception {
        mc.openScreen(parent); staticField(Root033Qa.class.getName(), "networkIsolated").setBoolean(null, false);
        call(preference, "setAs", savedStart); call(preferences, "save");
        call(manager, "removeHud", hud, true);
        for (var entry : SOURCE_STATE.entrySet()) entry.getKey().set(null, entry.getValue());
        mc.player.abilities.flying = flying; mc.player.setPositionAndAngles(x, y, z, yaw, pitch);
        restoreFile(serverFile, serverBytes); restoreFile(oldServerFile, oldServerBytes);
        resize(width, height); stat(MANAGER, "invalidate");
    }
    private static void restoreFile(Path path, byte[] bytes) throws Exception { if (bytes == null) Files.deleteIfExists(path); else Files.write(path, bytes); }
    private static boolean equal(Path path, byte[] bytes) throws Exception { return bytes == null ? !Files.exists(path) : Files.exists(path) && Arrays.equals(bytes, Files.readAllBytes(path)); }
    private static void capture(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
