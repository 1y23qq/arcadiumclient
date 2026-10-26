package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumServersScreen;
import io.github.arcadiumclient.runtime.qa.mixin.MouseInputAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.options.ServerList;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.gui.widget.ButtonWidget;
import io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess;
import org.polyfrost.oneconfig.api.platform.v1.Platform;

public final class SessionUiQa {
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static int ticks, stage, stageTicks, target, roundTrips;
    private static boolean done;
    private static Object previousWorld;
    private static Screen menu;
    private static float x, y;
    public static int resourceReloads, soundReloads, clientPingCleanup;
    private static int previousResourceReloads, previousSoundReloads;
    private static Object selectedPacks;
    public static boolean enabled() { return System.getProperty("arcadium.release052.output") != null; }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static void stage(int next) throws Exception {
        stage = next; stageTicks = 0;
        result.put("stage", stage);
        Files.writeString(output().resolveSibling("progress.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }

    private static Path output() { return Path.of(System.getProperty("arcadium.release052.output")); }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use the owned offline fixture");
            require(++ticks < 1500, "Session UI QA timed out at stage " + stage);
            stageTicks++;
            if (stage == 0 && ticks > 60) {
                ClientPreferences.setSetupComplete(true);
                mc.options.pauseOnUnfocus = false;
                ServerList servers = new ServerList(mc);
                servers.load();
                while (servers.size() > 0) servers.remove(0);
                for (int i = 0; i < 2; i++) servers.add(new ServerListEntry("Local QA " + i, System.getProperty("arcadium.release051.server" + i), false));
                servers.save();
                openServers(); stage(1);
            } else if (stage == 1 && stageTicks > 15) {
                float[] point = point("Local QA " + target);
                x = point[0]; y = point[1];
                result.put("pointer", List.of(x, y, Platform.screen().pixelRatio()));
                if (Boolean.getBoolean("arcadium.release052.externalMouse")) { stage(5); return; }
                pointer(x, y, true); stage(2);
            } else if (stage == 2 && stageTicks > 1) {
                pointer(x, y, false); stage(3);
            } else if (stage == 3 && stageTicks > 1) {
                if (roundTrips == 0) {
                    if (stageTicks < 10) return;
                    float[] point = point("Join server"); x = point[0]; y = point[1];
                    result.put("joinButton", true);
                }
                pointer(x, y, true); stage(4);
            } else if (stage == 4 && stageTicks > 1) {
                pointer(x, y, false); stage(5);
            } else if (stage == 5 && mc.world != null && mc.world != previousWorld && mc.player != null && mc.screen == null) {
                result.put("nativeDoubleClick", true);
                if (++roundTrips < 6) {
                    previousWorld = mc.world; target = 1 - target;
                    openServers(); stage(1);
                } else {
                    result.put("serverChanges", roundTrips);
                    menu = new GameMenuScreen(); mc.openScreen(menu);
                    stage(6);
                }
            } else if (stage == 6 && stageTicks > 10) {
                ButtonWidget reload = ((ScreenAccess) menu).arcadium$buttons().stream().filter(button -> button.message.equals("Reload Resources")).findFirst().orElseThrow();
                require(reload.x == 8 && reload.y == menu.height - 28 && reload.active, "Reload Resources is not available in the bottom-left corner");
                selectedPacks = new ArrayList<>(mc.options.resourcePacks);
                previousResourceReloads = resourceReloads; previousSoundReloads = soundReloads;
                x = reload.x + reload.getWidth()/2f; y = reload.y + 10;
                pointer(x, y, true); stage(7);
            } else if (stage == 7 && stageTicks > 20) {
                pointer(x, y, false);
                require(resourceReloads == previousResourceReloads + 1 && soundReloads > previousSoundReloads, "The reload button did not reload both resources and sound");
                require(mc.options.resourcePacks.equals(selectedPacks), "Reloading changed the selected resource packs");
                require(mc.screen == menu && mc.getNetworkHandler().getConnection().isConnected(), "Reloading closed the menu or disconnected the server");
                result.put("reloadResourcesAndSound", true); result.put("resourcePacksPreserved", true);
                result.put("reloadButtonPosition", List.of(8, menu.height - 28));
                require(clientPingCleanup == 0, "Server ping shutdown blocked the client thread");
                result.put("clientThreadPingCleanup", clientPingCleanup);
                finish(null);
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void openServers() {
        Minecraft mc = Minecraft.getInstance();
        menu = new ArcadiumServersScreen(mc.world == null ? new TitleScreen() : new GameMenuScreen());
        mc.openScreen(menu);
    }

    private static float[] point(String text) throws Exception {
        Object scene = field(menu, "sceneOrNull");
        require(scene != null, "The server list has no rendered scene");
        Object root = call(call(field(scene, "mainOwner"), "getSemanticsOwner"), "getRootSemanticsNode");
        for (Object node : nodes(root)) {
            for (Object value : (Iterable<?>) call(node, "getConfig")) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
                if (!"Text".equals(call(entry.getKey(), "getName")) || !(entry.getValue() instanceof Collection<?> values)) continue;
                for (Object item : values) if (text.equals(call(item, "getText"))) {
                    Object rect = call(node, "getBoundsInRoot");
                    return new float[]{(((Number)call(rect,"getLeft")).floatValue()+((Number)call(rect,"getRight")).floatValue())/2,
                        (((Number)call(rect,"getTop")).floatValue()+((Number)call(rect,"getBottom")).floatValue())/2};
                }
            }
        }
        throw new IllegalStateException("The server row is missing: " + text);
    }

    private static List<Object> nodes(Object node) throws Exception {
        List<Object> result = new ArrayList<>(); result.add(node);
        for (Object child : (Collection<?>) call(node, "getChildren")) result.addAll(nodes(child));
        return result;
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object call(Object target, String name, Object... arguments) throws Exception {
        for (Method method : target.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
            try { method.setAccessible(true); return method.invoke(target, arguments); }
            catch (IllegalArgumentException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    private static void pointer(float x, float y, boolean down) throws Exception {
        float ratio = (float) Platform.screen().pixelRatio();
        Field poll = org.lwjgl.input.Mouse.class.getDeclaredField("pollState");
        poll.setAccessible(true);
        Object state = poll.get(null);
        Field mx = state.getClass().getField("x"), my = state.getClass().getField("y");
        double oldX = mx.getDouble(state), oldY = my.getDouble(state);
        try {
            mx.setDouble(state, x * ratio);
            my.setDouble(state, Minecraft.getInstance().height - y * ratio - 1);
            if (down) ((MouseInputAccess) menu).release$press((int)x, (int)y, 0);
            else ((MouseInputAccess) menu).release$release((int)x, (int)y, 0);
        } finally { mx.setDouble(state, oldX); my.setDouble(state, oldY); }
    }

    private static void finish(Throwable error) {
        if (done) return; done = true;
        result.put("passed", error == null); result.put("synthetic", true);
        if (error != null) { result.put("error", error.toString()); error.printStackTrace(); }
        try { Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
