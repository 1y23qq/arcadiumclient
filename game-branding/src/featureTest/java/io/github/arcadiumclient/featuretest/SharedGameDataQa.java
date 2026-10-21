// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest;

import com.google.gson.GsonBuilder;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;

public final class SharedGameDataQa {
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static int ticks, phase;
    private static boolean done;
    private static Path base, game, report;
    private static String stage;
    private static Screen screen;
    private static byte[] beforeOpening;

    private SharedGameDataQa() {}

    public static void tick() {
        if (!Boolean.getBoolean("arcadium.sharedGameData") || done) return;
        var client = Minecraft.getInstance();
        try {
            if (++ticks == 1) {
                require(client.getSession().getUsername().equals("ArcadiumTest") && client.getSession().getAccessToken().equals("0"), "QA requires its synthetic offline account");
                base = Path.of(System.getProperty("arcadium.sharedGameData.base")).toRealPath();
                game = client.gameDir.toPath().toRealPath();
                stage = System.getProperty("arcadium.sharedGameData.stage");
                report = base.resolve(stage + ".json");
                require(game.equals(base.resolve("primary").toRealPath()), "Minecraft did not use the common user data directory");
                var runtime = Path.of("").toAbsolutePath().toRealPath();
                require(runtime.equals(base.resolve(stage.equals("native") ? "legacy" : "primary").toRealPath()), "Unexpected runtime working directory");
                RESULT.put("stage", stage);
                RESULT.put("gameDir", game.toString());
                RESULT.put("runtimeDir", runtime.toString());
                RESULT.put("syntheticSession", true);
            }
            require(ticks < 1200, "Shared game data QA timed out");
            if (ticks < 100) return;
            if (phase == 0) {
                if (stage.equals("write189")) {
                    Files.createDirectories(game.resolve("saves/Shared Game Data"));
                    Files.write(game.resolve("saves/Shared Game Data/level.dat"), metadata());
                    Files.createDirectories(game.resolve("screenshots"));
                    Files.writeString(game.resolve("screenshots/shared-data.txt"), "primary screenshot marker");
                    byte[] servers = servers();
                    Files.write(game.resolve("servers.dat"), servers);
                    Files.write(base.resolve("expected-servers.dat"), servers);
                    Files.createDirectories(game.resolve("resourcepacks/Shared folder pack"));
                    Files.writeString(game.resolve("resourcepacks/Shared folder pack/pack.mcmeta"), "{\"pack\":{\"pack_format\":1,\"description\":\"Shared pack\"}}");
                    client.options.gamma = 0.37f;
                    client.options.guiScale = 2;
                    client.options.save();
                    Files.writeString(game.resolve("options.txt"), Files.readString(game.resolve("options.txt")) + "sharedDataUnknown:retain:this:value\n");
                    RESULT.put("primaryFilesWritten", true);
                    finish(client, true, null);
                    return;
                }
                require(Math.abs(client.options.gamma - (stage.equals("native") ? 0.37f : 0.73f)) < 0.001f, "Gamma did not follow the other version");
                require(client.options.guiScale == 2, "GUI scale was not shared");
                require(Files.readString(game.resolve("screenshots/shared-data.txt")).equals("primary screenshot marker"), "Screenshot storage is not shared");
                require(java.util.Arrays.equals(Files.readAllBytes(game.resolve("servers.dat")), Files.readAllBytes(base.resolve("expected-servers.dat"))), "Server list storage is not shared");
                require(Files.exists(game.resolve("resourcepacks/Shared folder pack/pack.mcmeta")), "Folder resource pack is not shared");
                require(Files.readString(game.resolve("options.txt")).contains("sharedDataUnknown:retain:this:value"), "Unknown setting was erased");
                RESULT.put("settingsScreenshotsAndPacksShared", true);
                if (stage.equals("native")) {
                    client.options.gamma = 0.73f;
                    client.options.save();
                    require(Files.readString(game.resolve("options.txt")).contains("sharedDataUnknown:retain:this:value"), "Native saving erased unknown settings");
                } else {
                    require(Files.isDirectory(game.resolve("saves/Shared Game Data/region")), "Native world was not saved into common storage");
                    try (var backups = Files.list(game.resolve("backups"))) {
                        var archive = backups.filter(path -> path.toString().endsWith(".zip")).findFirst().orElseThrow();
                        try (var zip = new ZipFile(archive.toFile())) {
                            require(zip.getEntry("Shared Game Data/level.dat") != null, "World backup omitted metadata");
                        }
                    }
                    RESULT.put("nativeWorldAndBackupVisibleIn189", true);
                }
                screen = (Screen) Class.forName("io.github.arcadiumclient.runtime.client.gui.ArcadiumWorldsScreen")
                    .getConstructor(Screen.class).newInstance((Object) null);
                client.openScreen(screen);
                phase = 1;
                return;
            }
            if (phase == 1) {
                Object selected = null;
                for (Object world : (Iterable<?>) field(screen, "worlds")) {
                    if (field(world, "id").equals("Shared Game Data")) selected = world;
                }
                if (selected == null) return;
                for (var method : screen.getClass().getDeclaredMethods()) {
                    if (method.getName().equals("play") && method.getParameterCount() == 1) {
                        method.setAccessible(true);
                        method.invoke(screen, selected);
                        break;
                    }
                }
                if (stage.equals("native")) {
                    require(field(screen, "dialog") != null, "Opening a 1.8 world in 1.7 skipped its confirmation");
                    beforeOpening = Files.readAllBytes(game.resolve("saves/Shared Game Data/level.dat"));
                    phase = 3;
                    return;
                }
                phase = 2;
                return;
            }
            if (phase == 3) {
                if (!click("Back up and open")) return;
                RESULT.put("actualBackupConfirmationButtonUsed", true);
                phase = 2;
                return;
            }
            if (phase == 2 && client.world != null && client.player != null && client.screen == null) {
                if (stage.equals("native")) {
                    try (var backups = Files.list(game.resolve("backups"))) {
                        Path backup = backups.filter(path -> path.toString().endsWith(".zip")).findFirst().orElseThrow();
                        try (var zip = new ZipFile(backup.toFile())) {
                            byte[] original = zip.getInputStream(zip.getEntry("Shared Game Data/level.dat")).readAllBytes();
                            require(java.util.Arrays.equals(beforeOpening, original), "The backup did not preserve the world before it was opened");
                        }
                        RESULT.put("newerWorldConfirmationAndBackup", backup.toString());
                    }
                }
                RESULT.put("actualSharedWorldOpened", true);
                finish(client, true, null);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(client, false, failure.toString());
        }
    }

    private static byte[] metadata() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(10); out.writeUTF("Data");
            out.writeByte(8); out.writeUTF("LevelName"); out.writeUTF("Shared Game Data");
            out.writeByte(3); out.writeUTF("version"); out.writeInt(19133);
            out.writeByte(4); out.writeUTF("RandomSeed"); out.writeLong(410);
            out.writeByte(3); out.writeUTF("GameType"); out.writeInt(1);
            out.writeByte(8); out.writeUTF("generatorName"); out.writeUTF("flat");
            out.writeByte(6); out.writeUTF("BorderSize"); out.writeDouble(60000000);
            out.writeByte(0); out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] servers() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(9); out.writeUTF("servers"); out.writeByte(10); out.writeInt(1);
            out.writeByte(8); out.writeUTF("name"); out.writeUTF("Shared QA server");
            out.writeByte(8); out.writeUTF("ip"); out.writeUTF("127.0.0.1:9");
            out.writeByte(0); out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static Object field(Object target, String name) throws Exception {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException ignored) {
            var getter = target.getClass().getDeclaredMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            getter.setAccessible(true);
            return getter.invoke(target);
        }
    }

    private static boolean click(String text) throws Exception {
        Class<?> type = screen.getClass();
        java.lang.reflect.Field sceneField = null;
        while (type != null) {
            try { sceneField = type.getDeclaredField("sceneOrNull"); break; }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        require(sceneField != null, "The world screen has no Compose scene field");
        sceneField.setAccessible(true);
        Object scene = sceneField.get(screen);
        if (scene == null) return false;
        var nodes = new java.util.ArrayList<Object>();
        collectNodes(invoke(invoke(field(scene, "mainOwner"), "getSemanticsOwner"), "getRootSemanticsNode"), nodes);
        for (Object layer : (java.util.Collection<?>) field(scene, "layers")) {
            collectNodes(invoke(invoke(invoke(layer, "getOwner"), "getSemanticsOwner"), "getRootSemanticsNode"), nodes);
        }
        java.util.Collections.reverse(nodes);
        for (Object node : nodes) {
            var properties = new LinkedHashMap<String, Object>();
            for (Object value : (Iterable<?>) invoke(node, "getConfig")) {
                var entry = (Map.Entry<?, ?>) value;
                properties.put((String) invoke(entry.getKey(), "getName"), entry.getValue());
            }
            if (!properties.containsKey("OnClick") || !(properties.get("Text") instanceof java.util.Collection<?> values)) continue;
            for (Object value : values) {
                if (text.equals(invoke(value, "getText"))) {
                    require(Boolean.TRUE.equals(invoke(invoke(properties.get("OnClick"), "getAction"), "invoke")), "World backup button rejected its click");
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectNodes(Object node, java.util.List<Object> nodes) throws Exception {
        nodes.add(node);
        for (Object child : (java.util.Collection<?>) invoke(node, "getChildren")) collectNodes(child, nodes);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        var method = java.util.stream.Stream.concat(java.util.Arrays.stream(target.getClass().getDeclaredMethods()),
            java.util.Arrays.stream(target.getClass().getMethods()))
            .filter(value -> value.getName().equals(name) && value.getParameterCount() == args.length).findFirst().orElseThrow();
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void finish(Minecraft client, boolean passed, String error) {
        done = true;
        RESULT.put("passed", passed);
        if (error != null) RESULT.put("error", error);
        try { Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(RESULT)); }
        catch (Exception failure) { failure.printStackTrace(); }
        client.stop();
    }
}
