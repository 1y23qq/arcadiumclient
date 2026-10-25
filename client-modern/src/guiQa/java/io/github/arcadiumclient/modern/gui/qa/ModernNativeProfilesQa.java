// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.internal.compat.YACLCompat;

final class ModernNativeProfilesQa {
    private final Path output;
    private final String previous = ConfigManager.activeProfile();
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String first = "Arcadium Native A " + suffix, second = "Arcadium Native B " + suffix;
    private final ConfigClassHandler<?> night, screens;
    private final Object nightInstance, screensInstance;
    private final boolean previousNight, previousCleaner;
    private final float previousOpacity;
    private final Color previousColor;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<Path, byte[]> rootFiles = new LinkedHashMap<>();
    private final ModernOslProfilesQa osl;
    private Option<Boolean> option;
    private Path firstFolder, secondFolder;
    private boolean rootNight, rootCleaner;
    private float rootOpacity;
    private Color rootColor;

    static CompletableFuture<Map<String, Object>> run(Path output) {
        try { return new ModernNativeProfilesQa(output).run(); }
        catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    }
    private ModernNativeProfilesQa(Path output) throws Exception {
        this.output = output;
        night = handler("dev.microcontrollers.betternightvision.config.BetterNightVisionConfig");
        screens = handler("dev.microcontrollers.betterscreens.config.BetterScreensConfig");
        nightInstance = night.instance(); screensInstance = screens.instance();
        previousNight = bool(nightInstance, "disableNightVision"); previousCleaner = bool(nightInstance, "cleanerNightVision");
        previousOpacity = number(screensInstance, "containerBackgroundOpacity"); previousColor = color();
        osl = Boolean.getBoolean("arcadium.guiQa.oslProfiles") ? new ModernOslProfilesQa(output) : null;
    }
    private CompletableFuture<Map<String, Object>> run() {
        CompletableFuture<Void> checks = step(() -> {
            Path isolation = output.getParent().getParent().toAbsolutePath().normalize();
            require(isolation.getFileName().toString().equals("ArcadiumModern050Qa") && FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize().startsWith(isolation), "Native profile QA requires the isolated test profile");
            ConfigManager.openProfile("");
            night.save(); screens.save();
            night.configClass().getMethod("configScreen", Screen.class).invoke(null, Minecraft.getInstance().screen);
            ConfigManager.createProfile(first); firstFolder = ConfigManager.active().getFolder(); ConfigManager.openProfile("");
            ConfigManager.createProfile(second); secondFolder = ConfigManager.active().getFolder(); ConfigManager.openProfile("");
            night.save(); screens.save();
            for (String file : new String[]{"betternightvision.json", "betterscreens.json"}) {
                Path path = ConfigManager.active().getFolder().resolve(file);
                rootFiles.put(path, Files.readAllBytes(path));
            }
            rootNight = bool(nightInstance, "disableNightVision"); rootCleaner = bool(nightInstance, "cleanerNightVision");
            rootOpacity = number(screensInstance, "containerBackgroundOpacity"); rootColor = color();
            if (osl != null) osl.prepare(first, firstFolder, second, secondFolder);
            seed(firstFolder, true, 17, new int[]{34, 160, 255, 255}, "A");
            seed(secondFolder, false, 64, new int[]{241, 54, 176, 128}, "B");
            option = nativeNightOption();
            report.put("nativeStagedOptionRegistered", option != null);
            require(option != null, "The actual native NightVision config screen did not register its pending option");
            option.requestSet(false); require(!option.pendingValue(), "The actual native option did not accept a staged edit");
            ConfigManager.openProfile(first);
        }).thenComposeAsync(ignored -> step(() -> {
            verify(true, false, 17, 0xFF22A0FF, "A");
            if (osl != null) { osl.verify(false, .4f, "A"); osl.edit(.6f); }
            require(option.pendingValue(), "Opening legacy profileA did not clear the stale native YACL pending value");
            screensInstance.getClass().getField("containerBackgroundOpacity").setFloat(screensInstance, 19f);
            screensInstance.getClass().getField("containerLabelColor").set(screensInstance, new Color(10, 80, 120, 220));
            night.save(); screens.save();
            saved(firstFolder, "A", 19, 0xDC0A5078);
            unchangedRoot();
            option.requestSet(true);
            ConfigManager.openProfile(second);
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            verify(false, true, 64, 0x80F136B0, "B");
            if (osl != null) { osl.saved(firstFolder, "A", false, .6f); osl.verify(true, .8f, "B"); osl.edit(.9f); }
            require(!option.pendingValue(), "Opening legacy profileB retained the previous native YACL pending value");
            screensInstance.getClass().getField("containerBackgroundOpacity").setFloat(screensInstance, 66f);
            night.save(); screens.save();
            saved(secondFolder, "B", 66, 0x80F136B0);
            unchangedRoot();
            ConfigManager.openProfile(first);
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            verify(true, false, 19, 0xDC0A5078, "savedA");
            if (osl != null) { osl.saved(secondFolder, "B", true, .9f); osl.verify(false, .6f, "savedA"); }
            ConfigManager.openProfile(second);
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            verify(false, true, 66, 0x80F136B0, "savedB");
            if (osl != null) osl.verify(true, .9f, "savedB");
            ConfigManager.openProfile("");
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(() -> {
            verify(rootNight, rootCleaner, rootOpacity, rootColor.getRGB(), "rootRestored");
            if (osl != null) osl.complete();
            unchangedRoot();
            report.put("unknownFieldsPreserved", true); report.put("rawLegacyColorsPreserved", true);
            report.put("rootJsonUnchanged", true); report.put("sameNativeInstances", true);
            report.put("nativeStagedOptionRefreshed", option != null);
        }), QaTickScheduler.EXECUTOR);

        checks = checks.thenComposeAsync(ignored -> ModernAnimationProfilesQa.run(output).thenAccept(value -> report.put("animations", value)), QaTickScheduler.EXECUTOR);
        return checks.handleAsync((ignored, failure) -> {
            ConfigManager.openProfile(previous);
            return QaTickScheduler.afterTicks(3).thenApplyAsync(restored -> {
                try {
                    require(bool(nightInstance, "disableNightVision") == previousNight && bool(nightInstance, "cleanerNightVision") == previousCleaner &&
                            Math.abs(number(screensInstance, "containerBackgroundOpacity") - previousOpacity) < .001 && color().equals(previousColor), "Native profile QA did not restore the previous live config values");
                    report.put("restored", true); report.put("passed", failure == null);
                    if (osl != null) report.put("oslProfiles", osl.restored());
                    if (failure != null) report.put("error", failure.toString());
                    Files.writeString(output.resolve("native-profiles.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
                    if (failure != null) throw new CompletionException(failure);
                    return report;
                } catch (Exception writing) { throw new CompletionException(writing); }
            }, QaTickScheduler.EXECUTOR);
        }, QaTickScheduler.EXECUTOR).thenCompose(value -> value);
    }
    private void verify(boolean disabled, boolean cleaner, float opacity, int argb, String key) throws Exception {
        require(night.instance() == nightInstance && screens.instance() == screensInstance, "Native profile import replaced cached config objects");
        boolean actualDisabled = bool(night.instance(), "disableNightVision"), actualCleaner = bool(night.instance(), "cleanerNightVision");
        float actualOpacity = number(screens.instance(), "containerBackgroundOpacity"); int actualColor = color().getRGB();
        report.put(key, Map.of("nightVisionEnabled", !actualDisabled, "disableNightVision", actualDisabled, "cleanerNightVision", actualCleaner,
                "containerBackgroundOpacity", actualOpacity, "containerLabelArgb", Integer.toHexString(actualColor)));
        require(actualDisabled == disabled && actualCleaner == cleaner && Math.abs(actualOpacity - opacity) < .001 && actualColor == argb, "Native config values did not match legacy profile " + key);
    }
    private void saved(Path folder, String profile, float opacity, int argb) throws Exception {
        for (String name : new String[]{"betternightvision.json", "betterscreens.json"}) {
            JsonObject json = read(folder.resolve(name));
            require(json.getAsJsonObject("arcadiumQaUnknown").get("profile").getAsString().equals(profile), "Named native save discarded an unknown legacy field");
            if (name.equals("betterscreens.json")) {
                require(Math.abs(json.get("containerBackgroundOpacity").getAsFloat() - opacity) < .001, "Native opacity save targeted the wrong profile");
                JsonObject color = json.getAsJsonObject("containerLabelColor");
                require(color.get("class").getAsString().equals("org.polyfrost.compose.render.PolyColor") && color.get("arcadiumQaMeta").getAsString().equals("keep"), "Native color save lost the backward-readable wrapper or unknown metadata");
                JsonArray rgba = color.getAsJsonArray("value");
                require((rgba.get(3).getAsInt() << 24 | rgba.get(0).getAsInt() << 16 | rgba.get(1).getAsInt() << 8 | rgba.get(2).getAsInt()) == argb, "Saved legacy RGBA differs from the actual native color");
            }
            Path destination = output.resolve("legacy-profile-fixtures").resolve(profile).resolve(name);
            Files.createDirectories(destination.getParent()); Files.copy(folder.resolve(name), destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private void unchangedRoot() throws Exception { for (var file : rootFiles.entrySet()) require(Arrays.equals(file.getValue(), Files.readAllBytes(file.getKey())), "A named native save changed root JSON " + file.getKey().getFileName()); }
    private static void seed(Path folder, boolean disabled, float opacity, int[] rgba, String label) throws Exception {
        var unknown = new JsonObject(); unknown.addProperty("profile", label); unknown.addProperty("retainNumber", 37);
        var night = new JsonObject(); night.addProperty("disableNightVision", disabled); night.addProperty("cleanerNightVision", !disabled); night.add("arcadiumQaUnknown", unknown);
        var screens = new JsonObject(); screens.addProperty("containerBackgroundOpacity", opacity); screens.add("arcadiumQaUnknown", unknown.deepCopy());
        var color = new JsonObject(); color.addProperty("class", "org.polyfrost.compose.render.PolyColor"); color.addProperty("arcadiumQaMeta", "keep");
        var value = new JsonArray(); for (int channel : rgba) value.add(channel); color.add("value", value); screens.add("containerLabelColor", color);
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(folder.resolve("betternightvision.json"), gson.toJson(night)); Files.writeString(folder.resolve("betterscreens.json"), gson.toJson(screens));
    }
    @SuppressWarnings("unchecked") private static Option<Boolean> nativeNightOption() throws Exception {
        var contexts = YACLCompat.class.getDeclaredField("contexts"); contexts.setAccessible(true);
        Object context = ((Map<?, ?>) contexts.get(null)).get("betternightvision");
        if (context == null) return null;
        var tracked = context.getClass().getDeclaredField("boundOptions"); tracked.setAccessible(true);
        for (Object value : ((Map<?, ?>) tracked.get(context)).keySet()) if (value instanceof Option<?> option &&
                option.name().getContents() instanceof TranslatableContents translation && translation.getKey().endsWith("disable-night-vision")) return (Option<Boolean>) option;
        throw new IllegalStateException("The registered native NightVision config has no disable-night-vision option");
    }
    private static ConfigClassHandler<?> handler(String type) throws Exception { return (ConfigClassHandler<?>) Class.forName(type).getField("CONFIG").get(null); }
    private static boolean bool(Object instance, String name) throws Exception { return instance.getClass().getField(name).getBoolean(instance); }
    private static float number(Object instance, String name) throws Exception { return instance.getClass().getField(name).getFloat(instance); }
    private Color color() throws Exception { return (Color) screens.instance().getClass().getField("containerLabelColor").get(screens.instance()); }
    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static CompletableFuture<Void> step(Checked action) { try { action.run(); return QaTickScheduler.afterTicks(3); } catch (Throwable failure) { return CompletableFuture.failedFuture(failure); } }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
