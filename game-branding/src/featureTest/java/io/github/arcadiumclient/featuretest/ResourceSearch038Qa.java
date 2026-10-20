// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.ResourcePacksScreen;
import net.minecraft.client.gui.screen.Screen;

public final class ResourceSearch038Qa {
    private static final String ROOT = "Arcadium Search038 QA";
    private static final String NEEDLE = ROOT + "/Closed/Deep/§bNeedle §lPack.zip";
    private static final String ACTIVE = ROOT + "/Active/§dActive §lNeedle.zip";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static Screen screen, parent;
    private static int stage, ticks;
    private static boolean begun, done;

    private ResourceSearch038Qa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
            "Resource search QA requires the isolated offline session");
        if (!begun) {
            begun = true;
            checks.put("resourceSearch038", RESULT);
            parent = mc.screen;
            createFixtures(mc.getResourcePacks().getDirectory().toPath());
            mc.openScreen(new ResourcePacksScreen(parent));
            screen = mc.screen;
            require(screen.getClass().getSimpleName().equals("ArcadiumResourcePacksScreen"), "The native resource pack screen was not replaced");
            List<Object> selected = new ArrayList<>((Collection<?>) call(screen, "getActive"));
            for (int index = 0; index < 24; index++) selected.add(ROOT + "/Active/Pack " + index + ".zip");
            selected.add(ACTIVE);
            call(screen, "setActive", selected);
            call(screen, "setExpanded", Set.of(ROOT, ROOT + "/Available", ROOT + "/Closed", ROOT + "/Closed/Deep"));
            verifyNestedRows();
        }
        if (++ticks < 20) return false;
        switch (stage) {
            case 0 -> {
                require(lists().size() == 2, "Both resource pack lists must expose scrolling");
                for (Object list : lists()) {
                    Object action = call(properties(list).get("ScrollToIndex"), "getAction");
                    require(Boolean.TRUE.equals(call(action, "invoke", 12)), "A pack list rejected scrolling");
                }
            }
            case 1 -> {
                for (Object list : lists()) require(scrollValue(list) > 0, "The pack list did not scroll before the query changed");
                setSearch("Search038");
            }
            case 2 -> {
                for (Object list : lists()) require(scrollValue(list) == 0, "A changed search query retained the old scroll position");
                RESULT.put("availableAndActiveScrollReset", true);
                setSearch("ＮＥＥＤＬＥ　ＰＡＣＫ");
            }
            case 3 -> {
                require(hasText("Needle Pack.zip"), "The visible search missed a colored pack inside a nested folder");
                verifyColor("Needle Pack.zip", 0xff55ffff);
                capture(mc, "resource-search038-available.png");
                setSearch("§bneedle   §lpack");
            }
            case 4 -> {
                require(hasText("Needle Pack.zip"), "Formatting codes in a query prevented a match");
                RESULT.put("nestedColoredAndNormalizedQueries", true);
                setSearch("active needle");
            }
            case 5 -> {
                require(hasText("Active Needle.zip"), "The search did not reveal an already active resource pack");
                require(!hasText("Needle Pack.zip"), "The available list did not apply all query tokens");
                verifyColor("Active Needle.zip", 0xffff55ff);
                capture(mc, "resource-search038-active.png");
                RESULT.put("activePackSearch", true);
                RESULT.put("minecraftFormattingPreserved", true);
                mc.openScreen(parent);
                done = true;
                return true;
            }
            default -> throw new IllegalStateException("Unknown resource search QA stage");
        }
        stage++;
        ticks = 0;
        return false;
    }

    private static void verifyNestedRows() throws Exception {
        Class<?> functions = Class.forName("io.github.arcadiumclient.runtime.client.gui.ArcadiumResourcePacksScreenKt");
        Method method = java.util.Arrays.stream(functions.getDeclaredMethods()).filter(value -> value.getName().equals("packRows")).findFirst().orElseThrow();
        method.setAccessible(true);
        var active = (Collection<?>) call(screen, "getActive");
        var available = new ArrayList<>();
        for (Object pack : (Collection<?>) call(screen, "getPacks")) if (!active.contains(call(pack, "getId"))) available.add(pack);
        for (String query : List.of("needle pack", "§bneedle §lpack", "ＮＥＥＤＬＥ　ＰＡＣＫ", "closed deep needle", "qagreentext")) {
            List<?> rows = (List<?>) method.invoke(null, available, Set.of(), query, call(screen, "getSort"));
            List<Object> packs = new ArrayList<>();
            for (Object row : rows) if (row.getClass().getSimpleName().equals("Pack")) packs.add(call(row, "getPack"));
            require(packs.size() == 1 && NEEDLE.equals(call(packs.get(0), "getId")), "A collapsed nested pack was missed by query: " + query);
            require(rows.stream().filter(row -> row.getClass().getSimpleName().equals("Folder")).count() == 3,
                "Searching failed to reveal the complete folder path");
        }
        RESULT.put("collapsedNestedQueries", 5);
    }

    private static void createFixtures(Path directory) throws Exception {
        for (int index = 0; index < 24; index++) {
            writePack(directory.resolve(ROOT + "/Available/Pack " + index + ".zip"), "Available search fixture");
            writePack(directory.resolve(ROOT + "/Active/Pack " + index + ".zip"), "Active search fixture");
        }
        writePack(directory.resolve(NEEDLE), "§aQAGreenText\u00a0nested description");
        writePack(directory.resolve(ACTIVE), "§dAlready selected fixture");
    }

    private static void writePack(Path path, String description) throws Exception {
        Files.createDirectories(path.getParent());
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("pack.mcmeta"));
            output.write(new Gson().toJson(Map.of("pack", Map.of("pack_format", 1, "description", description))).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static List<Object> nodes() throws Exception {
        Field sceneField = null;
        for (Class<?> type = screen.getClass(); type != null && sceneField == null; type = type.getSuperclass()) {
            try { sceneField = type.getDeclaredField("sceneOrNull"); } catch (NoSuchFieldException ignored) { }
        }
        require(sceneField != null, "The pack browser has no Compose scene field");
        sceneField.setAccessible(true);
        Object scene = sceneField.get(screen);
        require(scene != null, "The pack browser has no active Compose scene");
        Field owner = scene.getClass().getDeclaredField("mainOwner");
        owner.setAccessible(true);
        return descendants(call(call(owner.get(scene), "getSemanticsOwner"), "getRootSemanticsNode"));
    }

    private static List<Object> descendants(Object node) throws Exception {
        var result = new ArrayList<>();
        result.add(node);
        for (Object child : (Collection<?>) call(node, "getChildren")) result.addAll(descendants(child));
        return result;
    }

    private static Map<String, Object> properties(Object node) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Object item : (Iterable<?>) call(node, "getConfig")) {
            var entry = (Map.Entry<?, ?>) item;
            values.put((String) call(entry.getKey(), "getName"), entry.getValue());
        }
        return values;
    }

    private static List<Object> lists() throws Exception {
        var result = new ArrayList<>();
        for (Object node : nodes()) if (properties(node).containsKey("ScrollToIndex")) result.add(node);
        return result;
    }

    private static float scrollValue(Object node) throws Exception {
        return ((Number) call(call(properties(node).get("VerticalScrollAxisRange"), "getValue"), "invoke")).floatValue();
    }

    private static Object textNode(String text) throws Exception {
        for (Object node : nodes()) {
            if (properties(node).get("Text") instanceof Collection<?> values) {
                for (Object value : values) if (text.equals(call(value, "getText"))) return node;
            }
        }
        throw new IllegalStateException("Visible resource pack text was not found: " + text);
    }

    private static boolean hasText(String text) throws Exception {
        try { textNode(text); return true; } catch (IllegalStateException absent) { return false; }
    }

    private static void verifyColor(String text, int expected) throws Exception {
        for (Object value : (Collection<?>) properties(textNode(text)).get("Text")) {
            for (Object span : (Collection<?>) call(value, "getSpanStyles")) {
                Object style = call(span, "getItem");
                Method color = java.util.Arrays.stream(style.getClass().getMethods())
                    .filter(method -> method.getName().startsWith("getColor-") && method.getParameterCount() == 0).findFirst().orElseThrow();
                if ((int) (((Number) color.invoke(style)).longValue() >>> 32) == expected) return;
            }
        }
        throw new AssertionError("The resource pack title lost its formatting color: " + text);
    }

    private static void setSearch(String text) throws Exception {
        for (Object node : nodes()) {
            Object entry = properties(node).get("SetText");
            if (entry == null) continue;
            Object action = call(entry, "getAction");
            Object value = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class)
                .newInstance(text, List.of(), List.of());
            require(Boolean.TRUE.equals(call(action, "invoke", value)), "The visible resource pack search rejected text");
            return;
        }
        throw new IllegalStateException("The pack search input was not found");
    }

    private static Object call(Object target, String name, Object... arguments) throws Exception {
        Method method = java.util.stream.Stream.concat(java.util.Arrays.stream(target.getClass().getDeclaredMethods()), java.util.Arrays.stream(target.getClass().getMethods()))
            .filter(value -> value.getName().equals(name) && value.getParameterCount() == arguments.length)
            .filter(value -> {
                for (int index = 0; index < arguments.length; index++) {
                    Class<?> type = value.getParameterTypes()[index];
                    if (type == int.class) type = Integer.class;
                    if (arguments[index] != null && !type.isInstance(arguments[index])) return false;
                }
                return true;
            }).findFirst().orElseThrow(() -> new NoSuchMethodException(target.getClass().getName() + "." + name));
        method.setAccessible(true);
        try { return method.invoke(target, arguments); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }

    private static void capture(Minecraft mc, String file) {
        Screenshot.take(mc.gameDir, file, mc.width, mc.height, mc.getRenderTarget());
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
