// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.qa.mixin.MouseInputAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePacksScreen;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;
import org.polyfrost.oneconfig.api.platform.v1.Platform;

public final class ResourcePackSearch065Qa {
    private static final String ROOT = "Arcadium Search065 QA";
    private static final String ALPHA = ROOT + "/Active/Alpha065.zip";
    private static final String BETA = ROOT + "/Active/Beta065.zip";
    private static final String NEEDLE = ROOT + "/Available/Closed/Needle065.zip";
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final GsonBuilder JSON = new GsonBuilder().setPrettyPrinting();
    private static Screen screen, parent;
    private static List<String> original;
    private static int ticks, wait, stage, dragTick;
    private static boolean done, dragging;
    private static float activeScroll;
    private static Point from, to;

    private ResourcePackSearch065Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.resourceSearch065.output") != null; }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
                "Resource pack QA requires the isolated offline fixture");
            require(++ticks < 1200, "Resource pack QA timed out at stage " + stage);
            if (dragging) { drag(mc); return; }
            if (screen == null) {
                if (ticks < 60 || mc.screen == null || mc.world != null) return;
                ClientPreferences.setSetupComplete(true);
                mc.options.pauseOnUnfocus = false;
                parent = mc.screen;
                Path directory = mc.getResourcePacks().getDirectory().toPath();
                for (String id : List.of(ALPHA, BETA, NEEDLE)) pack(directory.resolve(id));
                for (int index = 0; index < 24; index++) {
                    pack(directory.resolve(ROOT + "/Available/Overflow " + index + ".zip"));
                    pack(directory.resolve(ROOT + "/Active/Overflow " + index + ".zip"));
                }
                mc.openScreen(new ResourcePacksScreen(parent));
                screen = mc.screen;
                require(screen.getClass().getSimpleName().equals("ArcadiumResourcePacksScreen"), "The native resource pack entry point was not replaced");
                original = active();
                List<String> selected = new ArrayList<>(List.of(ALPHA, BETA));
                for (int index = 0; index < 24; index++) selected.add(ROOT + "/Active/Overflow " + index + ".zip");
                state("active", selected);
                state("expanded", Set.of(ROOT, ROOT + "/Available"));
                return;
            }
            if (++wait < 20) return;
            wait = 0;
            switch (stage++) {
                case 0 -> {
                    require(lists().size() == 2, "Both resource pack panes must expose scrolling");
                    for (Object list : lists()) action(list, "ScrollToIndex", 10);
                }
                case 1 -> {
                    require(scroll(lists().get(0)) > 0 && scroll(lists().get(1)) > 0, "Both panes must scroll before searching");
                    activeScroll = scroll(lists().get(1));
                    search("Needle065");
                }
                case 2 -> {
                    require(scroll(lists().get(0)) == 0, "Searching Available retained its old scroll position");
                    require(scroll(lists().get(1)) == activeScroll, "Searching Available changed the Active scroll position");
                    report.put("activeScrollPreserved", true);
                    state("active", List.of(ALPHA, BETA));
                }
                case 3 -> {
                    require(text("Needle065.zip"), "The query did not reveal the nested Available pack");
                    require(text("Alpha065.zip") && text("Beta065.zip"), "The Available query hid nonmatching Active packs");
                    capture(mc, "resource-search065-nested.png");
                    search("no-pack-matches-this-query");
                }
                case 4 -> {
                    require(text("No matching packs"), "The nonmatching Available query did not empty its pane");
                    require(text("Alpha065.zip") && text("Beta065.zip"), "The empty Available result hid Active packs");
                    require(active().equals(List.of(ALPHA, BETA)), "Searching changed the Active priority");
                    report.put("onlyAvailableFiltered", true);
                    Object top = call(description("Resource pack " + ALPHA, 0), "getBoundsInRoot");
                    from = body(BETA);
                    to = new Point(number(top, "getLeft") + 60, number(top, "getTop") + 2);
                    dragging = true;
                    dragTick = 0;
                    cursor(mc, from);
                    int[] point = gui(mc, from);
                    ((MouseInputAccess) screen).release$press(point[0], point[1], 0);
                }
                case 5 -> {
                    require(active().equals(List.of(BETA, ALPHA)), "Dragging Active priority failed while Available had no matches");
                    report.put("dragReorderWithNoAvailableMatches", true);
                    click(mc, description("Move down", 0));
                }
                case 6 -> {
                    require(active().equals(List.of(ALPHA, BETA)), "The Active move button failed while Available had no matches");
                    report.put("moveButtonWithNoAvailableMatches", true);
                    click(mc, description("Disable pack", 0));
                }
                case 7 -> {
                    require(active().equals(List.of(BETA)), "The Active disable button failed while Available had no matches");
                    require(text("Beta065.zip") && !text("Alpha065.zip"), "Disabling an Active pack bypassed the Available query");
                    report.put("disableWithNoAvailableMatches", true);
                    capture(mc, "resource-search065-active.png");
                    search("Alpha065");
                }
                case 8 -> {
                    require(text("Alpha065.zip") && text("Beta065.zip"), "The disabled pack did not return to Available or Active was filtered");
                    report.put("disabledPackReturnsToAvailable", true);
                    state("active", original);
                    mc.openScreen(parent);
                    finish(null);
                }
                default -> throw new IllegalStateException("Unknown resource pack QA stage");
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private static void pack(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("pack.mcmeta"));
            output.write("{\"pack\":{\"pack_format\":1,\"description\":\"Search regression fixture\"}}".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    @SuppressWarnings("unchecked") private static List<String> active() throws Exception {
        return List.copyOf((List<String>) call(field(screen, "active$delegate"), "getValue"));
    }
    private static void state(String name, Object value) throws Exception { call(field(screen, name + "$delegate"), "setValue", value); }
    private static void action(Object node, String key, Object... arguments) throws Exception {
        require(Boolean.TRUE.equals(call(call(properties(node).get(key), "getAction"), "invoke", arguments)), "The GUI action was rejected: " + key);
    }
    private static void search(String value) throws Exception {
        Object text = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class)
            .newInstance(value, List.of(), List.of());
        for (Object node : nodes()) if (properties(node).containsKey("SetText")) { action(node, "SetText", text); return; }
        throw new IllegalStateException("The native search input is missing");
    }
    private static boolean text(String expected) throws Exception {
        for (Object node : nodes()) if (properties(node).get("Text") instanceof Collection<?> values)
            for (Object value : values) if (expected.equals(call(value, "getText"))) return true;
        return false;
    }
    private static Object description(String expected, int index) throws Exception {
        for (Object node : nodes()) if (properties(node).get("ContentDescription") instanceof Collection<?> values
            && values.contains(expected) && index-- == 0) return node;
        throw new IllegalStateException("The native pointer target is missing: " + expected);
    }
    private static List<Object> lists() throws Exception {
        List<Object> result = new ArrayList<>();
        for (Object node : nodes()) if (properties(node).containsKey("ScrollToIndex")) result.add(node);
        return result;
    }
    private static float scroll(Object node) throws Exception {
        return ((Number) call(call(properties(node).get("VerticalScrollAxisRange"), "getValue"), "invoke")).floatValue();
    }
    private static List<Object> nodes() throws Exception {
        Object scene = field(screen, "sceneOrNull");
        return descendants(call(call(field(scene, "mainOwner"), "getSemanticsOwner"), "getRootSemanticsNode"));
    }
    private static List<Object> descendants(Object node) throws Exception {
        List<Object> result = new ArrayList<>(); result.add(node);
        for (Object child : (Collection<?>) call(node, "getChildren")) result.addAll(descendants(child));
        return result;
    }
    private static Map<String, Object> properties(Object node) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object item : (Iterable<?>) call(node, "getConfig")) {
            var entry = (Map.Entry<?, ?>) item;
            result.put((String) call(entry.getKey(), "getName"), entry.getValue());
        }
        return result;
    }
    private static Point body(String id) throws Exception {
        Object bounds = call(description("Resource pack " + id, 0), "getBoundsInRoot");
        return new Point(number(bounds, "getLeft") + 60, (number(bounds, "getTop") + number(bounds, "getBottom")) / 2);
    }
    private static void click(Minecraft mc, Object node) throws Exception {
        Object bounds = call(node, "getBoundsInRoot");
        Point point = new Point((number(bounds, "getLeft") + number(bounds, "getRight")) / 2,
            (number(bounds, "getTop") + number(bounds, "getBottom")) / 2);
        cursor(mc, point); int[] gui = gui(mc, point);
        ((MouseInputAccess) screen).release$press(gui[0], gui[1], 0);
        ((MouseInputAccess) screen).release$release(gui[0], gui[1], 0);
    }
    private static void drag(Minecraft mc) throws Exception {
        int age = ++dragTick;
        double fraction = Math.clamp((age - 3) / 12., 0, 1);
        Point point = new Point(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction);
        cursor(mc, point); int[] gui = gui(mc, point);
        if (age > 3 && age < 18) {
            Method drag = Arrays.stream(Screen.class.getDeclaredMethods()).filter(method -> Arrays.equals(method.getParameterTypes(),
                new Class<?>[]{int.class, int.class, int.class, long.class})).findFirst().orElseThrow();
            drag.setAccessible(true); drag.invoke(screen, gui[0], gui[1], 0, age * 50L);
        }
        if (age == 18) ((MouseInputAccess) screen).release$release(gui[0], gui[1], 0);
        if (age > 22) { dragging = false; wait = 0; }
    }
    private static void cursor(Minecraft mc, Point point) { double ratio = Platform.screen().pixelRatio(); Mouse.setCursorPosition((int) Math.round(point.x * ratio), mc.height - (int) Math.round(point.y * ratio) - 1); }
    private static int[] gui(Minecraft mc, Point point) { double ratio = Platform.screen().pixelRatio(); return new int[]{(int) Math.round(point.x * screen.width / (mc.width / ratio)), (int) Math.round(point.y * screen.height / (mc.height / ratio))}; }
    private static double number(Object target, String getter) throws Exception { return ((Number) call(target, getter)).doubleValue(); }
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
    private static void capture(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void finish(Throwable failure) {
        done = true; report.put("passed", failure == null); report.put("stage", stage); report.put("syntheticOfflineFixture", true);
        if (failure != null) { report.put("error", failure.toString()); failure.printStackTrace(); capture(Minecraft.getInstance(), "resource-search065-failure.png"); }
        try {
            Path output = Path.of(System.getProperty("arcadium.resourceSearch065.output"));
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, JSON.create().toJson(report));
        } catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private record Point(double x, double y) { }
}
