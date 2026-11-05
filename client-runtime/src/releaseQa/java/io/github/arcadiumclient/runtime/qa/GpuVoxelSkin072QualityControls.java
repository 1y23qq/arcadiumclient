// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;

public final class GpuVoxelSkin072QualityControls {
    private static final String YAW = System.getProperty("arcadium.voxelQuality072.yaw");
    private static final String TIME = System.getProperty("arcadium.voxelQuality072.time");
    private static final String SHADOWS = System.getProperty("arcadium.voxelQuality072.entityShadows");
    private static final float OFFSET = YAW == null ? 0 : Float.parseFloat(YAW);
    private static final long TIME_OF_DAY = TIME == null ? 6000 : Long.parseLong(TIME);
    private static boolean hooked;
    private static long actorTicks, worldTicks, captureBypasses;
    private static List<Float> observedAngles = List.of();
    private static final Map<String,Float> observedScales = new TreeMap<>();
    private static final List<Map<String,Object>> captureProofs = new ArrayList<>();
    private static final long[] phaseCapturedParts = new long[12];
    private static MethodHandle partLookup;
    private static long observedTime;
    private static Boolean observedEntityShadows;
    private GpuVoxelSkin072QualityControls() {}

    private static boolean active() {
        return (YAW != null || TIME != null || SHADOWS != null) && PlayerPipeline072Qa.enabled()
                && System.getProperty("arcadium.playerPipeline072.mode", "visual").equals("visual");
    }
    public static boolean captureOnly() {
        if (!captureRequested()) return false;
        captureBypasses++; hook(); return true;
    }
    public static boolean captureRequested() {
        return Boolean.getBoolean("arcadium.voxelQuality072.captureOnly") && PlayerPipeline072Qa.enabled()
                && System.getProperty("arcadium.playerPipeline072.mode", "visual").equals("visual");
    }
    public static void armCapture() { if (captureRequested()) Arrays.fill(phaseCapturedParts, 0); }
    public static void nativeCapture(Object mesh) {
        if (!captureRequested()) return;
        try {
            if (partLookup == null) {
                Class<?> bridge = Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinBridge");
                partLookup = MethodHandles.privateLookupIn(bridge, MethodHandles.lookup()).unreflect(bridge.getDeclaredMethod("part", Object.class));
            }
            int part = (int)partLookup.invoke(mesh); if (part >= 0 && part < 12) phaseCapturedParts[part]++;
        } catch (Throwable failure) { throw new IllegalStateException("Cannot verify the actual captured voxel part", failure); }
    }
    public static void captureProof(long nativeParts, long analyticParts, long partMask) {
        int observedMask = 0; long observedParts = 0;
        for (int part=0;part<12;part++) { observedParts += phaseCapturedParts[part]; if (phaseCapturedParts[part] != 0) observedMask |= 1<<part; }
        if (nativeParts <= 0 || analyticParts != 0 || (partMask & 0xFC0) != 0xFC0 || (observedMask & 0xFC0) != 0xFC0
                || observedParts != nativeParts || captureBypasses <= 0)
            throw new IllegalStateException("The capture-only voxel fixture did not use all six actual native GPU skin parts");
        if (captureProofs.size() < 16) captureProofs.add(Map.of("nativeParts", nativeParts, "analyticParts", analyticParts,
                "nativePartMask", Long.toHexString(partMask), "phaseObservedPartMask", Integer.toHexString(observedMask),
                "phaseObservedPartCounts", phaseCapturedParts.clone(), "analyticCallsBypassed", captureBypasses));
        hook();
    }
    public static void skinOffsets() throws ReflectiveOperationException {
        if (!PlayerPipeline072Qa.enabled() || !System.getProperty("arcadium.playerPipeline072.mode", "visual").equals("visual")) return;
        String[][] properties = {{"baseSize", "BaseVoxelSize"}, {"bodyWidth", "BodyVoxelWidthSize"}, {"headSize", "HeadVoxelSize"}};
        boolean requested = false;
        for (String[] entry : properties) requested |= System.getProperty("arcadium.voxelQuality072."+entry[0]) != null;
        if (!requested) return;
        Class<?> mod = Class.forName("org.polyfrost.skinlayers.SkinLayers");
        Object config = mod.getMethod("getConfig").invoke(mod.getField("INSTANCE").get(null));
        for (String[] entry : properties) {
            String raw = System.getProperty("arcadium.voxelQuality072."+entry[0]);
            if (raw != null) {
                float value = Float.parseFloat(raw);
                if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid QA voxel scale");
                config.getClass().getMethod("set"+entry[1], float.class).invoke(config, value);
            }
            observedScales.put(entry[0], (float)config.getClass().getMethod("get"+entry[1]).invoke(config));
        }
        hook();
    }
    public static void actor(RemoteClientPlayerEntity actor) {
        if (!active()) return;
        actor.yaw += OFFSET; actor.lastYaw += OFFSET; actor.bodyYaw += OFFSET; actor.lastBodyYaw += OFFSET;
        actor.headYaw += OFFSET; actor.lastHeadYaw += OFFSET;
        actorTicks++;
        if (observedAngles.isEmpty()) observedAngles = List.of(actor.yaw, actor.bodyYaw, actor.headYaw);
        hook();
    }
    public static void world() {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (SHADOWS != null) {
            if (!SHADOWS.equals("true") && !SHADOWS.equals("false")) throw new IllegalArgumentException("Invalid QA entityShadows option");
            mc.options.entityShadows = Boolean.parseBoolean(SHADOWS);
        }
        observedEntityShadows = mc.options.entityShadows;
        if (mc.world == null) return;
        for (var player : mc.world.players) {
            if (player instanceof RemoteClientPlayerEntity remote && player.getClass().getName().equals("io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa$Actor")) actor(remote);
        }
        if (TIME != null) mc.world.setTimeOfDay(TIME_OF_DAY);
        observedTime = mc.world.getTimeOfDay(); worldTicks++; hook();
    }
    private static void hook() {
        if (hooked) return; hooked = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String file = System.getProperty("arcadium.playerPipeline072.output"); if (file == null) return;
            try {
                Map<String,Object> result = new LinkedHashMap<>();
                result.put("visualOnly", true); result.put("yawOffset", OFFSET); result.put("timeOfDay", TIME_OF_DAY);
                result.put("actorTicksControlled", actorTicks); result.put("worldTicksControlled", worldTicks);
                result.put("observedFirstActorAngles", observedAngles); result.put("observedWorldTime", observedTime);
                result.put("captureOnly", Boolean.getBoolean("arcadium.voxelQuality072.captureOnly")); result.put("analyticCallsBypassed", captureBypasses);
                result.put("observedVoxelScales", observedScales);
                result.put("requestedEntityShadows", SHADOWS); result.put("observedEntityShadows", observedEntityShadows);
                result.put("captureProofs", captureProofs);
                Files.writeString(Path.of(file).resolveSibling("voxel-quality-controls.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            } catch (Exception failure) { failure.printStackTrace(); }
        }, "Arcadium voxel visual controls"));
    }
}
