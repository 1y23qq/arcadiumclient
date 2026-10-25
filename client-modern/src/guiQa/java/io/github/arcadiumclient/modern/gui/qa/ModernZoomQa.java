// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;

final class ModernZoomQa {
    private ModernZoomQa() {}

    static Map<String, Object> run(String profile, String previous, Path output) throws Exception {
        var report = new LinkedHashMap<String, Object>();
        var type = Class.forName("dev.isxander.zoomify.config.ZoomifySettings");
        Object owner = type.getField("Companion").get(null);
        Method initial = owner.getClass().getMethod("getInitialZoom");
        Object entry = initial.invoke(owner);
        Method get = entry.getClass().getMethod("get"); get.setAccessible(true);
        Method set = entry.getClass().getMethod("set", Object.class); set.setAccessible(true);
        Method save = owner.getClass().getMethod("saveToFile");
        Method encode = type.getMethod("encodeStart", DynamicOps.class);
        ConfigManager.openProfile(previous);
        double original = ((Number) get.invoke(entry)).doubleValue();
        Path rootFile = FabricLoader.getInstance().getConfigDir().resolve("zoomify.json");
        byte[] originalRoot = Files.exists(rootFile) ? Files.readAllBytes(rootFile) : null;
        Path named = ConfigManager.profileDir(profile).resolve("zoomify.json");
        var fixture = new JsonObject();
        fixture.addProperty("initialZoom", 11);
        fixture.addProperty("keybindScrolling", true);
        fixture.addProperty("arcadiumQaUnknown", "retain-me");
        Files.writeString(named, new GsonBuilder().setPrettyPrinting().create().toJson(fixture));
        try {
            ConfigManager.openProfile(profile);
            double loaded = ((Number) get.invoke(entry)).doubleValue();
            report.put("loadedLegacyInitialZoom", loaded);
            require(loaded == 11d, "Native Zoomify codec did not load the legacy named profile value");
            set.invoke(entry, 15);
            save.invoke(owner);
            JsonObject written = JsonParser.parseString(Files.readString(named)).getAsJsonObject();
            require(written.get("initialZoom").getAsDouble() == 15d, "Native Zoomify save did not target the active profile");
            require(written.get("arcadiumQaUnknown").getAsString().equals("retain-me"), "Native Zoomify save discarded an unknown shared field");
            byte[] unchangedRoot = Files.exists(rootFile) ? Files.readAllBytes(rootFile) : null;
            require(Arrays.equals(originalRoot, unchangedRoot), "Named Zoomify save changed the root JSON");
            report.put("savedInitialZoom", 15d); report.put("unknownFieldPreserved", true); report.put("rootJsonUnchanged", true);
            report.put("nativeCodecEncodedInitialZoom", ((com.google.gson.JsonElement) ((DataResult<?>) encode.invoke(owner, JsonOps.INSTANCE)).getOrThrow()).getAsJsonObject().get("initialZoom").getAsDouble());
            if (originalRoot != null) report.put("rootSha256", java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(originalRoot)));
            ConfigManager.openProfile(previous);
            double returned = ((Number) get.invoke(entry)).doubleValue();
            require(returned == original, "Returning to the previous profile did not restore its native Zoomify value");
            report.put("previousInitialZoom", original); report.put("returnedInitialZoom", returned);
            ConfigManager.openProfile(profile);
            require(((Number) get.invoke(entry)).doubleValue() == 15d, "Named Zoomify setting did not survive a profile roundtrip");
            report.put("passed", true);
            return report;
        } catch (Exception failure) {
            report.put("passed", false); report.put("error", failure.toString()); throw failure;
        } finally {
            Files.writeString(output.resolve("zoom-profiles.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
