package io.github.arcadiumclient.featuretest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.ServerList;
import net.minecraft.client.options.ServerListEntry;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

final class PlayMenusQa {
    private static final String LEGACY = "arcadium-menu-qa-legacy";
    private static final String NEWER = "arcadium-menu-qa-newer";
    private static final Map<String, Object> CHECKS = new LinkedHashMap<>();
    private static int stage, substage, ticks, width, height;
    private static boolean begun, done;
    private static Screen parent, worlds;
    private static Object screen;
    private static byte[] futureBytes, serverBytes;
    private static boolean priorHideNewer;

    private PlayMenusQa() { }

    static void useScreen(Screen owner) { screen = owner; }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!Boolean.getBoolean("arcadium.playMenuQa") || done) return true;
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
            "Menu QA requires the isolated offline account");
        if (!begun) {
            begun = true;
            checks.put("playMenus", CHECKS);
            parent = mc.screen;
            width = mc.width; height = mc.height;
            Class<?> config = Class.forName("io.github.arcadiumclient.runtime.client.MenuPreferences");
            priorHideNewer = (boolean) config.getMethod("getHideNewerWorlds").invoke(null);
            config.getMethod("setHideNewerWorlds", boolean.class).invoke(null, true);
            Path saves = mc.gameDir.toPath().resolve("saves");
            require(!Files.exists(saves.resolve(LEGACY)) && !Files.exists(saves.resolve(NEWER)), "Menu fixtures already exist");
            Files.createDirectories(saves.resolve(LEGACY)); Files.createDirectories(saves.resolve(NEWER));
            Files.write(saves.resolve(LEGACY).resolve("level.dat"), world("QA Legacy", false));
            futureBytes = world("QA Newer", true);
            Files.write(saves.resolve(NEWER).resolve("level.dat"), futureBytes);
            Path servers = mc.gameDir.toPath().resolve("servers.dat");
            serverBytes = Files.isRegularFile(servers) ? Files.readAllBytes(servers) : null;
            mc.openScreen(new SelectWorldScreen(parent));
            screen = mc.screen;
            worlds = mc.screen;
            require(screen.getClass().getSimpleName().equals("ArcadiumWorldsScreen"), "The native singleplayer entry point was not replaced");
        }
        if (++ticks < 18) return false;
        switch (stage) {
            case 0 -> {
                if (Boolean.TRUE.equals(invoke(screen, "getLoading"))) return false;
                require(hasText("QA Legacy") && !hasText("QA Newer"), "Newer worlds are not hidden by default");
                capture(mc, "play-menus-01-worlds.png");
                click("Hide newer worlds");
            }
            case 1 -> {
                require(hasText("QA Newer"), "Disabling the newer-world filter did not reveal the world");
                if (substage == 0) { click("QA Newer"); return again(); }
                click("Play");
            }
            case 2 -> {
                if (substage == 0) {
                    require(invoke(screen, "getDialog") != null && hasText("Open anyway"), "A newer world skipped its safety confirmation");
                    capture(mc, "play-menus-02-newer-warning.png");
                    click("Cancel");
                    require(java.util.Arrays.equals(futureBytes, Files.readAllBytes(mc.gameDir.toPath().resolve("saves/" + NEWER + "/level.dat"))),
                        "Browsing or cancelling a newer world modified its metadata");
                    return again();
                }
                if (substage == 1) { click("Hide newer worlds"); return again(); }
                if (substage == 2) { click("QA Legacy"); return again(); }
                click("Rename");
            }
            case 3 -> {
                setText(1, "QA Renamed");
                click("Save");
            }
            case 4 -> {
                if (Boolean.TRUE.equals(invoke(screen, "getLoading"))) return false;
                require(hasText("QA Renamed") && !hasText("QA Legacy"), "Renaming did not update the real world metadata");
                click("QA Renamed");
                click("Delete");
            }
            case 5 -> {
                if (substage == 0) {
                    require(hasText("Delete world?"), "World deletion skipped confirmation");
                    click("Cancel");
                    return again();
                }
                require(Files.exists(mc.gameDir.toPath().resolve("saves/" + LEGACY + "/level.dat")), "Cancelling deletion removed the save");
                click("Delete");
            }
            case 6 -> click("Delete");
            case 7 -> {
                if (Boolean.TRUE.equals(invoke(screen, "getLoading"))) return false;
                require(!Files.exists(mc.gameDir.toPath().resolve("saves/" + LEGACY)), "Confirming deletion did not remove the fixture save");
                click("Create world");
            }
            case 8 -> {
                if (substage == 0) {
                    screen = mc.screen;
                    require(screen.getClass().getSimpleName().equals("ArcadiumCreateWorldScreen"), "Create world did not use the Arcadium screen");
                    Object delegate = field(screen, "delegate");
                    var buttons = (List<?>) invoke(delegate, "polyplus$buttons");
                    String options = buttons.stream().map(ButtonWidget.class::cast).filter(button -> button.id == 3).findFirst().orElseThrow().message;
                    capture(mc, "play-menus-07-create-world.png");
                    click(options);
                    return again();
                }
                if (substage == 1) {
                    require(Boolean.TRUE.equals(invoke(field(screen, "delegate"), "arcadium$moreOptions")), "More World Options did not reach the native generator settings");
                    setText(0, "ArcadiumSeed");
                    click("Done");
                    return again();
                }
                if (substage == 2) {
                    Object delegate = field(screen, "delegate");
                    require(Boolean.FALSE.equals(invoke(delegate, "arcadium$moreOptions")), "Done did not leave the generator options");
                    require("ArcadiumSeed".equals(invoke(delegate, "arcadium$currentSeed")), "The native generator did not retain the seed input");
                    click("Back");
                    return again();
                }
                require(mc.screen == worlds, "Create world did not return to the singleplayer menu");
                screen = worlds;
                Display.setDisplayMode(new DisplayMode(854, 480));
            }
            case 9 -> {
                if (substage == 0) {
                    if (mc.width != 854 || mc.height != 480) return false;
                    verifyBounds(mc, List.of("Singleplayer", "Hide newer worlds", "Create world", "Re-create", "Back"));
                    capture(mc, "play-menus-03-worlds-small.png");
                    Display.setDisplayMode(new DisplayMode(width, height));
                    mc.openScreen(new MultiplayerScreen(parent));
                    screen = mc.screen;
                    require(screen.getClass().getSimpleName().equals("ArcadiumServersScreen"), "The native multiplayer entry point was not replaced");
                    return again();
                }
                click("Add server");
            }
            case 10 -> {
                if (substage == 0) { setText(1, "QA One"); setText(2, "127.0.0.1:9"); return again(); }
                click("Save");
            }
            case 11 -> click("Add server");
            case 12 -> {
                if (substage == 0) { setText(1, "QA Two"); setText(2, "127.0.0.1:10"); return again(); }
                click("Save");
            }
            case 13 -> {
                require(hasText("QA One") && hasText("QA Two"), "Adding servers did not update the native saved list");
                List<?> entries = (List<?>) invoke(screen, "getEntries");
                Object second = entries.stream().filter(entry -> "QA Two".equals(((ServerListEntry) entry).name)).findFirst().orElseThrow();
                invoke(screen, "reorder", second, 0);
                ((ServerList) field(screen, "servers")).save();
                require(((List<?>) invoke(screen, "getEntries")).get(0) == second, "Server drag ordering did not change the real list");
                click("QA One"); click("Edit");
            }
            case 14 -> {
                setText(1, "QA Edited");
                click("Save");
            }
            case 15 -> {
                require(hasText("QA Edited") && !hasText("QA One"), "Editing a server did not update the list");
                capture(mc, "play-menus-04-servers.png");
                click("QA Edited"); click("Delete");
            }
            case 16 -> {
                require(hasText("Delete server?"), "Server deletion skipped confirmation");
                click("Cancel");
            }
            case 17 -> {
                require(hasText("QA Edited"), "Cancelling server deletion removed it");
                click("Delete");
            }
            case 18 -> click("Delete");
            case 19 -> {
                require(!hasText("QA Edited") && hasText("QA Two"), "Server deletion removed the wrong entry");
                click("Direct connect");
            }
            case 20 -> {
                require(hasText("Direct connect") && hasText("Join server"), "Direct connect is unavailable");
                click("Cancel");
                Display.setDisplayMode(new DisplayMode(854, 480));
            }
            case 21 -> {
                if (mc.width != 854 || mc.height != 480) return false;
                verifyBounds(mc, List.of("Multiplayer", "Add server", "Direct connect", "Edit", "Delete", "Back"));
                capture(mc, "play-menus-05-servers-small.png");
                Display.setDisplayMode(new DisplayMode(width, height));
                click("Back");
            }
            case 22 -> {
                require(mc.screen == parent, "Back did not return to the original parent screen");
                require(((java.util.concurrent.ExecutorService) field(screen, "pool")).isShutdown(), "Leaving multiplayer did not stop the pinger workers");
                require(field(screen, "detector") == null, "Leaving multiplayer did not release the LAN detector");
                createFormattedPack(mc.gameDir.toPath().resolve("resourcepacks/§bArcadium §lPixel§r.zip"));
                screen = Class.forName("io.github.arcadiumclient.runtime.client.gui.ArcadiumResourcePacksScreen").getConstructor(Screen.class).newInstance(parent);
                mc.openScreen((Screen) screen);
            }
            case 23 -> {
                verifyMinecraftText("Arcadium Pixel.zip", 0xFF55FFFF);
                verifyMinecraftText("Green description", 0xFF55FF55);
                capture(mc, "play-menus-06-pack-font.png");
                mc.openScreen(parent);
                Class<?> config = Class.forName("io.github.arcadiumclient.runtime.client.MenuPreferences");
                config.getMethod("setHideNewerWorlds", boolean.class).invoke(null, priorHideNewer);
                Path servers = mc.gameDir.toPath().resolve("servers.dat");
                if (serverBytes == null) Files.deleteIfExists(servers); else Files.write(servers, serverBytes);
                Path future = mc.gameDir.toPath().resolve("saves/" + NEWER);
                require(java.util.Arrays.equals(futureBytes, Files.readAllBytes(future.resolve("level.dat"))), "The newer-world fixture changed before cleanup");
                Files.delete(future.resolve("level.dat"));
                Files.delete(future);
                CHECKS.put("nativeEntryPoints", true);
                CHECKS.put("newerWorldFilterAndWarning", true);
                CHECKS.put("worldMetadataUnchangedByBrowsing", true);
                CHECKS.put("worldRenameDeleteConfirmation", true);
                CHECKS.put("nativeGeneratorOptions", true);
                CHECKS.put("serverAddEditDeleteDirect", true);
                CHECKS.put("serverOrderSaved", true);
                CHECKS.put("pingerAndLanCleanup", true);
                CHECKS.put("smallWindowControls", true);
                CHECKS.put("minecraftFontAndLegacyColors", true);
                CHECKS.put("completed", true);
                done = true;
                return true;
            }
            default -> throw new IllegalStateException("Unknown play menu QA phase");
        }
        stage++; substage = 0; ticks = 0;
        return false;
    }

    private static boolean again() { substage++; ticks = 0; return false; }

    private static void verifyMinecraftText(String text, int argb) throws Exception {
        Object node = textNode(text, false);
        Map<String, Object> properties = properties(node);
        boolean colored = false;
        for (Object piece : (Collection<?>) properties.get("Text")) {
            for (Object range : (Collection<?>) invoke(piece, "getSpanStyles")) {
                Object style = invoke(range, "getItem");
                Method color = java.util.Arrays.stream(style.getClass().getMethods()).filter(method -> method.getName().startsWith("getColor-") && method.getParameterCount() == 0).findFirst().orElseThrow();
                colored |= (int) (((Number) color.invoke(style)).longValue() >>> 32) == argb;
            }
        }
        require(colored, "The pack text lost its Minecraft formatting color: " + text);
        var layouts = new ArrayList<>();
        Object action = invoke(properties.get("GetTextLayoutResult"), "getAction");
        require(Boolean.TRUE.equals(invoke(action, "invoke", layouts)) && !layouts.isEmpty(), "Pack text layout is unavailable");
        Object family = invoke(invoke(invoke(layouts.get(0), "getLayoutInput"), "getStyle"), "getFontFamily");
        require(family.toString().contains("Minecraft-"), "Pack metadata did not use the Minecraft font: " + family);
    }

    static void verifyBounds(Minecraft mc, List<String> texts) throws Exception {
        for (String text : texts) {
            Object rect = invoke(textNode(text, false), "getBoundsInRoot");
            float left = ((Number) invoke(rect, "getLeft")).floatValue(), right = ((Number) invoke(rect, "getRight")).floatValue();
            float top = ((Number) invoke(rect, "getTop")).floatValue(), bottom = ((Number) invoke(rect, "getBottom")).floatValue();
            require(left >= 0 && top >= 0 && right > left && bottom > top && right <= mc.width && bottom <= mc.height,
                "A menu control is clipped at 854x480: " + text + " " + rect);
        }
    }

    private static void createFormattedPack(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write("{\"pack\":{\"pack_format\":1,\"description\":\"§aGreen description\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static byte[] world(String name, boolean newer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(10); out.writeUTF("Data");
            out.writeByte(8); out.writeUTF("LevelName"); out.writeUTF(name);
            out.writeByte(4); out.writeUTF("LastPlayed"); out.writeLong(System.currentTimeMillis() + 1000);
            out.writeByte(3); out.writeUTF("GameType"); out.writeInt(1);
            out.writeByte(3); out.writeUTF("version"); out.writeInt(19133);
            if (newer) { out.writeByte(3); out.writeUTF("DataVersion"); out.writeInt(3955); }
            out.writeByte(0); out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    static List<Object> allNodes() throws Exception {
        Class<?> type = screen.getClass();
        java.lang.reflect.Field sceneField;
        for (;;) {
            try { sceneField = type.getDeclaredField("sceneOrNull"); break; }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        sceneField.setAccessible(true);
        Object scene = sceneField.get(screen);
        require(scene != null, "The play menu has no rendered Compose scene");
        var result = nodes(invoke(invoke(field(scene, "mainOwner"), "getSemanticsOwner"), "getRootSemanticsNode"));
        for (Object layer : (Collection<?>) field(scene, "layers")) {
            Object owner = invoke(layer, "getOwner");
            result.addAll(nodes(invoke(invoke(owner, "getSemanticsOwner"), "getRootSemanticsNode")));
        }
        return result;
    }

    static Map<String, Object> properties(Object node) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : (Iterable<?>) invoke(node, "getConfig")) {
            var entry = (Map.Entry<?, ?>) value;
            result.put((String) invoke(entry.getKey(), "getName"), entry.getValue());
        }
        return result;
    }

    private static List<Object> nodes(Object node) throws Exception {
        var result = new ArrayList<>(); result.add(node);
        for (Object child : (Collection<?>) invoke(node, "getChildren")) result.addAll(nodes(child));
        return result;
    }

    static Object textNode(String text, boolean clickable) throws Exception {
        var all = allNodes();
        java.util.Collections.reverse(all);
        for (Object node : all) {
            var properties = properties(node);
            if (clickable && !properties.containsKey("OnClick")) continue;
            if (properties.get("Text") instanceof Collection<?> values) {
                for (Object value : values) if (text.equals(invoke(value, "getText"))) return node;
            }
        }
        throw new IllegalStateException("Play menu text was not found: " + text + " at stage " + stage);
    }

    static boolean hasText(String text) throws Exception {
        try { textNode(text, false); return true; } catch (IllegalStateException absent) { return false; }
    }

    static void click(String text) throws Exception {
        Object action = invoke(properties(textNode(text, true)).get("OnClick"), "getAction");
        require(Boolean.TRUE.equals(invoke(action, "invoke")), "Play menu click was rejected: " + text);
    }

    static void setText(int index, String text) throws Exception {
        var inputs = new ArrayList<>();
        for (Object node : allNodes()) if (properties(node).containsKey("SetText")) inputs.add(node);
        require(index < inputs.size(), "Play menu input was not found at index " + index);
        Object action = invoke(properties(inputs.get(index)).get("SetText"), "getAction");
        Object value = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class).newInstance(text, List.of(), List.of());
        require(Boolean.TRUE.equals(invoke(action, "invoke", value)), "The play menu input rejected text");
    }

    static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    static Object invoke(Object target, String name, Object... args) throws Exception {
        Method method = java.util.stream.Stream.concat(java.util.Arrays.stream(target.getClass().getDeclaredMethods()), java.util.Arrays.stream(target.getClass().getMethods()))
            .filter(value -> value.getName().equals(name) && value.getParameterCount() == args.length).findFirst().orElseThrow();
        method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException failure) { if (failure.getCause() instanceof Exception cause) throw cause; throw failure; }
    }

    static void capture(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
