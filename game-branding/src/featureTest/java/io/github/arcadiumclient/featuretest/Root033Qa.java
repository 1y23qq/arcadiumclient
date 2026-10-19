package io.github.arcadiumclient.featuretest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.options.ServerList;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

public final class Root033Qa {
    private static final String HEIGHT = "io.github.arcadiumclient.runtime.client.features.HeightLimitHud";
    private static final String SOURCE = "io.github.arcadiumclient.runtime.client.features.HeightLimitSource";
    private static final String COUNTER = "org.polyfrost.evergreenhud.client.hud.ItemCounterHud";
    private static final String MANAGER = "org.polyfrost.oneconfig.api.hud.v1.HudManager";
    private static final String[] QUERIES = {"minecraft:diamond", "minecraft:golden_apple", "minecraft:emerald"};
    private static final int[][] COUNTER_MODES = {{0, 1}, {1, 1}, {0, 0}, {1, 0}};
    private static final Map<String, Object> CHECKS = new LinkedHashMap<>();
    private static final List<Map<String, Object>> HEIGHT_SAMPLES = new ArrayList<>();
    private static final List<Map<String, Object>> COUNTER_SAMPLES = new ArrayList<>();
    private static final List<Object> createdHuds = new ArrayList<>();
    private static final Map<Field, Object> originalPackets = new LinkedHashMap<>();
    private static boolean begun, done, flying, networkIsolated, restored;
    private static volatile int blockedPings, blockedLan;
    private static int phase, ticks, heightCase, counterCase, width, height, openRevision;
    private static double x, y, z;
    private static float yaw, pitch;
    private static ItemStack[] inventory;
    private static byte[] serversBytes, serversOldBytes;
    private static Path serverPath, serverOldPath;
    private static Screen savedScreen, serverScreen;
    private static Object manager, heightHud, counterHud;
    private static Field limitsField;
    private static Object savedLimits;
    private static double scrolledValue;

    private Root033Qa() {}

    public static boolean isolatingNetwork() { return networkIsolated; }
    public static void blockedPing() { blockedPings++; }
    public static void blockedLan() { blockedLan++; }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        require(Boolean.getBoolean("arcadium.forgeQa") && Boolean.getBoolean("arcadium.release033Qa"), "Root 0.3.3 QA was called outside its fixture");
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
            "Root 0.3.3 QA requires the isolated offline account");
        try {
            if (!begun) begin(mc, checks);
            require(++ticks < 180, "Root 0.3.3 QA timed out at phase " + phase);
            switch (phase) {
                case 0 -> {
                    if (ticks < 12) return false;
                    verifyHeight(mc);
                    if (++heightCase < 6) {
                        selectHeight(mc);
                        ticks = 0;
                        return false;
                    }
                    selectKnownHeight(mc);
                    selectCounter();
                    phase = 1;
                    ticks = 0;
                }
                case 1 -> {
                    if (ticks < 15) return false;
                    verifyCounter(mc);
                    if (++counterCase < COUNTER_MODES.length) {
                        selectCounter();
                        ticks = 0;
                        return false;
                    }
                    prepareServers(mc);
                    phase = 2;
                    ticks = 0;
                }
                case 2 -> {
                    if (ticks < 25) return false;
                    require(scrollValue() == 0 && visible("Scroll QA 00"), "The saved server list did not open at the top");
                    openRevision = ((Number) invoke(serverScreen, "getOpenRevision")).intValue();
                    scrollTo(12);
                    phase = 3;
                    ticks = 0;
                }
                case 3 -> {
                    if (ticks < 20) return false;
                    scrolledValue = scrollValue();
                    require(scrolledValue > 0 && visible("Scroll QA 12") && !visible("Scroll QA 00"), "The real server list did not scroll to the requested row");
                    serverScreen.init();
                    phase = 4;
                    ticks = 0;
                }
                case 4 -> {
                    if (ticks < 15) return false;
                    require(((Number) invoke(serverScreen, "getOpenRevision")).intValue() == openRevision,
                        "Reinitializing an already open server screen changed its opening generation");
                    require(scrollValue() > 0 && !visible("Scroll QA 00"), "Reinitialization reset the active server viewport");
                    requestSize(854, 480);
                    phase = 5;
                    ticks = 0;
                }
                case 5 -> {
                    if (mc.width != 854 || mc.height != 480 || ticks < 20) return false;
                    require(mc.screen == serverScreen && scrollValue() > 0 && !visible("Scroll QA 00"), "Resizing reset the server list to the top");
                    CHECKS.put("serverResize", Map.of("beforeScroll", scrolledValue, "afterScroll", scrollValue(), "pixels", List.of(mc.width, mc.height)));
                    screenshot(mc, "root033-servers-scrolled-resize.png");
                    mc.openScreen(savedScreen);
                    require(Boolean.TRUE.equals(field(serverScreen.getClass(), "closed").get(serverScreen)), "Closing the server screen did not run its real lifecycle");
                    mc.openScreen(serverScreen);
                    phase = 6;
                    ticks = 0;
                }
                case 6 -> {
                    if (ticks < 25) return false;
                    require(((Number) invoke(serverScreen, "getOpenRevision")).intValue() == openRevision + 1, "Reopening did not advance the server screen opening generation");
                    require(scrollValue() == 0 && visible("Scroll QA 00"), "The reopened server list did not reset its first row");
                    require(blockedPings > 0 && blockedLan > 0, "The server viewport test did not isolate its network work");
                    CHECKS.put("serverReopen", Map.of("atTop", true, "openingGeneration", openRevision + 1, "blockedPings", blockedPings, "blockedLanSockets", blockedLan));
                    screenshot(mc, "root033-servers-reopened-top.png");
                    restore(mc);
                    phase = 7;
                    ticks = 0;
                }
                case 7 -> {
                    if (ticks < 20 || mc.width != width || mc.height != height) return false;
                    require(equalFile(serverPath, serversBytes) && equalFile(serverOldPath, serversOldBytes), "The server-list fixture was not restored byte for byte");
                    CHECKS.put("restored", true);
                    CHECKS.put("completed", true);
                    done = true;
                    return true;
                }
                default -> throw new IllegalStateException("Unknown root QA phase");
            }
            return false;
        } catch (Exception failure) {
            try { restore(mc); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        begun = true;
        checks.put("release033Root", CHECKS);
        CHECKS.put("heightLimit", HEIGHT_SAMPLES);
        CHECKS.put("itemCounterLayouts", COUNTER_SAMPLES);
        width = mc.width; height = mc.height;
        savedScreen = mc.screen;
        x = mc.player.x; y = mc.player.y; z = mc.player.z; yaw = mc.player.yaw; pitch = mc.player.pitch;
        flying = mc.player.abilities.flying;
        mc.player.abilities.flying = true;
        inventory = mc.player.inventory.items.clone();
        Arrays.fill(mc.player.inventory.items, null);
        mc.player.inventory.items[0] = new ItemStack(Items.DIAMOND, 5);
        mc.player.inventory.items[1] = new ItemStack(Items.GOLDEN_APPLE, 3);
        mc.player.inventory.items[2] = new ItemStack(Items.EMERALD, 11);
        manager = singleton(MANAGER);
        heightHud = makeHud(HEIGHT);
        counterHud = makeHud(COUNTER);
        require(Boolean.FALSE.equals(invoke(heightHud, "showByDefault")), "Height Limit HUD is enabled by default");
        require("hybedwars.json".equals(invoke(heightHud, "getConfigId")), "Height Limit HUD is not associated with HyBedwars");
        require(((Number) field(counterHud.getClass(), "arcadium$itemCounterLayout").get(counterHud)).intValue() == 0, "Item Counter does not default to vertical layout");
        invoke(heightHud, "setAbsolutePosition", 16f, 88f);
        invoke(counterHud, "setAbsolutePosition", 16f, 124f);
        invoke(counterHud, "setItem", (Object) QUERIES.clone());
        invoke(counterHud, "setShowName", false);
        invoke(counterHud, "setHideWhenEmpty", false);
        for (Object hud : List.of(heightHud, counterHud)) {
            invoke(hud, "setHidden", false);
            invoke(hud, "setShowBackground", true);
            invoke(hud, "setShowShadow", true);
            invoke(hud, "setTextScale", 1f);
            invoke(hud, "setStaticWidth", false);
        }
        Object minecraftFont = Arrays.stream(Class.forName("org.polyfrost.oneconfig.api.hud.v1.Font").getEnumConstants())
            .filter(value -> value.toString().equals("Minecraft")).findFirst().orElseThrow();
        invoke(counterHud, "setFont", minecraftFont);
        seedHeightData();
        selectHeight(mc);
        mc.openScreen(null);
    }

    @SuppressWarnings("unchecked")
    private static Object makeHud(String name) throws Exception {
        Object provider = ((Collection<?>) invoke(manager, "providers")).stream().filter(value -> value.getClass().getName().equals(name)).findFirst().orElseThrow();
        Object hud = invoke(provider, "make", (Object) null);
        require(hud != provider && Boolean.TRUE.equals(invoke(hud, "isReal")), "The fixture HUD did not use an independent real registration: " + name);
        createdHuds.add(hud);
        ((List<Object>) invoke(manager, "getActiveInstances")).add(hud);
        invoke(hud, "setup");
        Object revision = invoke(manager, "getInstancesRevision");
        invoke(revision, "setIntValue", ((Number) invoke(revision, "getIntValue")).intValue() + 1);
        staticCall(MANAGER, "invalidate");
        return hud;
    }

    private static void seedHeightData() throws Exception {
        Class<?> packets = Class.forName("tomeko.hybedwars.location.HypixelPackets");
        for (String name : List.of("onHypixel", "inBedwars", "currentMapName")) {
            Field field = field(packets, name);
            originalPackets.put(field, field.get(null));
        }
        Class<?> data = Class.forName("tomeko.hybedwars.heightlimit.HeightLimitData");
        limitsField = field(data, "bedwarsMaps");
        savedLimits = limitsField.get(null);
        Map<String, Object> fixture = new LinkedHashMap<>();
        if (savedLimits instanceof Map<?, ?> original) for (var entry : original.entrySet()) fixture.put((String) entry.getKey(), entry.getValue());
        Class<?> limits = Class.forName("tomeko.hybedwars.heightlimit.HeightLimitData$Limits");
        fixture.put("arcadium_qa_map", limits.getConstructor(int.class, int.class).newInstance(48, 113));
        fixture.put("arcadium_qa_second", limits.getConstructor(int.class, int.class).newInstance(32, 147));
        fixture.put("arcadium_qa_invalid", limits.getConstructor(int.class, int.class).newInstance(0, 999));
        limitsField.set(null, fixture);
        CHECKS.put("heightDataFixture", "real HyBedwars HeightLimitData.Limits, local data only");
    }

    private static void selectHeight(Minecraft mc) throws Exception {
        String[] maps = {"Arcadium QA Map", "Arcadium QA Map", "Arcadium QA Missing", "Arcadium QA Missing", "Arcadium QA Second", "Arcadium QA Invalid"};
        Class<?> packets = Class.forName("tomeko.hybedwars.location.HypixelPackets");
        field(packets, "onHypixel").setBoolean(null, true);
        field(packets, "inBedwars").setBoolean(null, heightCase != 0);
        field(packets, "currentMapName").set(null, maps[heightCase]);
        invoke(heightHud, "setShowUnknown", heightCase == 3);
        mc.player.setPositionAndAngles(x, heightCase == 4 ? 114.25 : 63.75, z, yaw, pitch);
        invoke(heightHud, "update");
        staticCall(MANAGER, "invalidate");
    }

    private static void selectKnownHeight(Minecraft mc) throws Exception {
        heightCase = 1;
        selectHeight(mc);
    }

    private static void verifyHeight(Minecraft mc) throws Exception {
        invoke(heightHud, "update");
        Object snapshot = staticCall(SOURCE, "snapshot");
        boolean visible = Boolean.TRUE.equals(invoke(heightHud, "shouldShow"));
        String text = (String) invoke(heightHud, "getText");
        Integer maximum = (Integer) invoke(snapshot, "getMaximum");
        boolean expectedVisible = heightCase == 1 || heightCase == 3 || heightCase == 4;
        Integer expectedMaximum = heightCase == 1 ? Integer.valueOf(113) : heightCase == 4 ? Integer.valueOf(147) : null;
        require(visible == expectedVisible, "Height Limit visibility differs from the real map context: " + heightCase);
        require(java.util.Objects.equals(maximum, expectedMaximum), "Height Limit used a global or invalid ceiling instead of the map limit: " + maximum);
        if (visible) {
            int actualHeight = (int) Math.floor(mc.player.y);
            require(text.equals(actualHeight + " / " + (maximum == null ? "?" : maximum)), "Height Limit did not show current floor(Y) and its map ceiling: " + text);
            require(!text.endsWith(" / 256"), "Height Limit used the world's global build limit");
            require(invoke(heightHud, "getRuntimeOrNull") != null && number(heightHud, "getRenderedW") > 0 && number(heightHud, "getRenderedH") > 0,
                "The real Height Limit HUD did not render");
        }
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("case", heightCase); sample.put("visible", visible); sample.put("text", text); sample.put("maximum", maximum);
        sample.put("dataMap", invoke(snapshot, "getMap"));
        HEIGHT_SAMPLES.add(sample);
        if (heightCase == 1 || heightCase == 4) screenshot(mc, "root033-height-map-" + heightCase + ".png");
    }

    private static void selectCounter() throws Exception {
        int[] mode = COUNTER_MODES[counterCase];
        Object property = invoke(invoke(counterHud, "getTree"), "getProp", "arcadium$itemCounterLayout");
        require(property != null, "The real Item Counter layout property is missing");
        invoke(property, "setAs", mode[0]);
        invoke(counterHud, "setShowIcon", mode[1] != 0);
        invoke(counterHud, "update");
        staticCall(MANAGER, "invalidate");
    }

    private static void verifyCounter(Minecraft mc) throws Exception {
        Object rowsState = field(counterHud.getClass(), "arcadium$rows").get(counterHud);
        List<?> rows = (List<?>) invoke(rowsState, "getValue");
        require(rows.size() == 3, "The Item Counter did not create three independent rows");
        int[] counts = {5, 3, 11};
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String text = (String) invoke(rows.get(i), "getText");
            require(text.contains(Integer.toString(counts[i])), "The real counter lost an inventory count: " + text);
            texts.add(text);
        }
        Object runtime = invoke(counterHud, "getRuntimeOrNull");
        require(runtime != null, "The registered Item Counter has no rendered runtime");
        Object root = invoke(runtime, "getRoot");
        List<Object> labels = descendants(root).stream().filter(node -> node.getClass().getSimpleName().equals("McTextNode")).toList();
        require(labels.size() == 3, "The rendered counter does not contain three measured text nodes");
        double rootWidth = number(root, "getWidth"), rootHeight = number(root, "getHeight");
        double scale = number(counterHud, "getEffectiveScale");
        require(rootWidth > 0 && rootHeight > 0 && Math.abs(rootWidth * scale - number(counterHud, "getRenderedW")) < 0.1
            && Math.abs(rootHeight * scale - number(counterHud, "getRenderedH")) < 0.1, "The actual counter bounds differ from its rendered content");
        boolean horizontal = COUNTER_MODES[counterCase][0] == 1;
        List<List<Double>> bounds = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            Object label = labels.get(i);
            double left = number(label, "getX"), top = number(label, "getY"), w = number(label, "getWidth"), h = number(label, "getHeight");
            require(left >= 0 && top >= 0 && left + w <= rootWidth + 0.1 && top + h <= rootHeight + 0.1,
                "A counter label is clipped outside its real HUD bounds");
            require(texts.get(i).equals(invoke(label, "getText")), "The counter layout reordered its labels");
            bounds.add(List.of(left, top, w, h));
            if (i > 0) {
                List<Double> previous = bounds.get(i - 1);
                if (horizontal) require(Math.abs(top - previous.get(1)) < 0.1 && left > previous.get(0) + previous.get(2), "Horizontal counters did not occupy one row");
                else require(Math.abs(left - previous.get(0)) < 0.1 && top >= previous.get(1) + previous.get(3), "Vertical counters did not occupy separate rows");
            }
        }
        if (horizontal) {
            Map<String, Object> vertical = COUNTER_SAMPLES.get(counterCase - 1);
            require(rootHeight < ((Number) vertical.get("height")).doubleValue() && rootWidth > ((Number) vertical.get("width")).doubleValue(),
                "Changing the counter layout did not recompute its measured width and height");
        }
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("layout", horizontal ? "Horizontal" : "Vertical"); sample.put("icons", COUNTER_MODES[counterCase][1] != 0);
        sample.put("width", rootWidth); sample.put("height", rootHeight); sample.put("labels", texts); sample.put("labelBounds", bounds);
        COUNTER_SAMPLES.add(sample);
        screenshot(mc, "root033-counter-" + counterCase + ".png");
    }

    private static void prepareServers(Minecraft mc) throws Exception {
        serverPath = mc.gameDir.toPath().resolve("servers.dat");
        serverOldPath = mc.gameDir.toPath().resolve("servers.dat_old");
        serversBytes = Files.exists(serverPath) ? Files.readAllBytes(serverPath) : null;
        serversOldBytes = Files.exists(serverOldPath) ? Files.readAllBytes(serverOldPath) : null;
        ServerList list = new ServerList(mc);
        while (list.size() > 0) list.remove(0);
        for (int i = 0; i < 40; i++) list.add(new ServerListEntry(String.format(java.util.Locale.ROOT, "Scroll QA %02d", i), "127.0.0.1:9", false));
        list.save();
        networkIsolated = true;
        mc.openScreen(new MultiplayerScreen(savedScreen));
        serverScreen = mc.screen;
        require(serverScreen != null && serverScreen.getClass().getSimpleName().equals("ArcadiumServersScreen"), "The real Arcadium server list did not open");
    }

    private static double scrollValue() throws Exception {
        for (Object node : nodes(semanticsRoot())) {
            Object axis = properties(node).get("VerticalScrollAxisRange");
            if (axis != null) return ((Number) invoke(invoke(axis, "getValue"), "invoke")).doubleValue();
        }
        throw new IllegalStateException("The real server list has no scroll semantics");
    }

    private static void scrollTo(int index) throws Exception {
        for (Object node : nodes(semanticsRoot())) {
            Object action = properties(node).get("ScrollToIndex");
            if (action == null) continue;
            require(Boolean.TRUE.equals(invoke(invoke(action, "getAction"), "invoke", index)), "The actual server list rejected its scroll action");
            return;
        }
        throw new IllegalStateException("The real server list exposes no ScrollToIndex action");
    }

    private static boolean visible(String text) throws Exception {
        for (Object node : nodes(semanticsRoot())) {
            Object content = properties(node).get("Text");
            if (content instanceof Collection<?> pieces) for (Object piece : pieces) if (text.equals(invoke(piece, "getText"))) return true;
        }
        return false;
    }

    private static Object semanticsRoot() throws Exception {
        Object scene = field(serverScreen.getClass(), "sceneOrNull").get(serverScreen);
        require(scene != null, "The server list has no rendered Compose scene");
        Object owner = field(scene.getClass(), "mainOwner").get(scene);
        return invoke(invoke(owner, "getSemanticsOwner"), "getRootSemanticsNode");
    }

    private static void requestSize(int targetWidth, int targetHeight) throws Exception {
        Display.setDisplayMode(new DisplayMode(targetWidth, targetHeight));
        try {
            Class<?> display = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
            long handle = (long) display.getMethod("getHandle").invoke(display.getMethod("instance").invoke(null));
            Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_SetWindowSize", long.class, int.class, int.class).invoke(null, handle, targetWidth, targetHeight);
        } catch (ClassNotFoundException ignored) {}
    }

    private static void restore(Minecraft mc) throws Exception {
        if (restored) return;
        restored = true;
        if (serverScreen != null && mc.screen == serverScreen) mc.openScreen(savedScreen);
        networkIsolated = false;
        for (Object hud : createdHuds) invoke(manager, "removeHud", hud, true);
        if (limitsField != null) limitsField.set(null, savedLimits);
        for (var entry : originalPackets.entrySet()) entry.getKey().set(null, entry.getValue());
        if (inventory != null) System.arraycopy(inventory, 0, mc.player.inventory.items, 0, inventory.length);
        mc.player.abilities.flying = flying;
        mc.player.setPositionAndAngles(x, y, z, yaw, pitch);
        if (serverPath != null) restoreFile(serverPath, serversBytes);
        if (serverOldPath != null) restoreFile(serverOldPath, serversOldBytes);
        if (mc.width != width || mc.height != height) requestSize(width, height);
        staticCall(MANAGER, "invalidate");
    }

    private static void restoreFile(Path path, byte[] bytes) throws Exception { if (bytes == null) Files.deleteIfExists(path); else Files.write(path, bytes); }
    private static boolean equalFile(Path path, byte[] bytes) throws Exception { return bytes == null ? !Files.exists(path) : Files.exists(path) && Arrays.equals(bytes, Files.readAllBytes(path)); }
    private static double number(Object target, String getter) throws Exception { return ((Number) invoke(target, getter)).doubleValue(); }
    private static List<Object> descendants(Object root) throws Exception { return nodes(root); }
    private static List<Object> nodes(Object node) throws Exception {
        List<Object> values = new ArrayList<>(); values.add(node);
        for (Object child : (Collection<?>) invoke(node, "getChildren")) values.addAll(nodes(child));
        return values;
    }
    private static Map<String, Object> properties(Object node) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Object entry : (Iterable<?>) invoke(node, "getConfig")) {
            Map.Entry<?, ?> item = (Map.Entry<?, ?>) entry;
            values.put((String) invoke(item.getKey(), "getName"), item.getValue());
        }
        return values;
    }
    private static Object singleton(String name) throws Exception { return Class.forName(name).getField("INSTANCE").get(null); }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static Object staticCall(String type, String name, Object... args) throws Exception { return invoke(Class.forName(type), name, args); }
    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Class<?> type = target instanceof Class<?> value ? value : target.getClass();
        List<Method> candidates = new ArrayList<>(Arrays.asList(type.getMethods()));
        for (Class<?> current = type; current != null; current = current.getSuperclass()) candidates.addAll(Arrays.asList(current.getDeclaredMethods()));
        for (Method method : candidates) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean compatible = true;
            for (int i = 0; i < args.length; i++) {
                Class<?> parameter = method.getParameterTypes()[i];
                if (parameter.isPrimitive()) parameter = parameter == boolean.class ? Boolean.class : parameter == int.class ? Integer.class : parameter == float.class ? Float.class : parameter == long.class ? Long.class : parameter;
                if (args[i] != null && !parameter.isInstance(args[i])) compatible = false;
            }
            if (!compatible) continue;
            method.setAccessible(true);
            try { return method.invoke(target instanceof Class<?> ? null : target, args); }
            catch (InvocationTargetException failure) { if (failure.getCause() instanceof Exception cause) throw cause; throw failure; }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + args.length);
    }
    private static void screenshot(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
