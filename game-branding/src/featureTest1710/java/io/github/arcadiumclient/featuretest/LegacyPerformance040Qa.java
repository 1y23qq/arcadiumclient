// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

final class LegacyPerformance040Qa {
    private static Screen previous;
    private static int began;
    private static boolean complete, previousBlur;
    private static Object blur;

    private LegacyPerformance040Qa() {}

    static boolean tick(Minecraft client, Map<String, Object> result, int tick) throws Exception {
        if (complete) return true;
        if (began == 0) {
            began = tick;
            previous = client.screen;
            blur = instance("org.polyfrost.polyblur.client.PolyBlurConfig");
            previousBlur = (boolean) call(blur, "isEnabled");
            call(blur, "setEnabled", false);
            client.openScreen(new PausedScene());
            return false;
        }
        if (tick - began < 30) return false;
        require(tick - began < 200, "The native renderer comparison never reached a stable scene");
        Class<?> terrain = Class.forName("io.github.arcadiumclient.performance.terrain.TerrainRenderer");
        Field nativePath = terrain.getDeclaredField("forceNativeSubmission"); nativePath.setAccessible(true);
        boolean priorNative = nativePath.getBoolean(null);
        require(Boolean.TRUE.equals(stat(terrain.getName(), "enabled")), "The optimized native terrain renderer is unavailable");
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int buffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int texture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        try {
            byte[] baseline = pixels(client, nativePath, true);
            byte[] optimized = pixels(client, nativePath, false);
            byte[] nativeRepeat = pixels(client, nativePath, true);
            if (!Arrays.equals(baseline, nativeRepeat)) return false;
            int channels = 0, maxDelta = 0;
            for (int i = 0; i < baseline.length; i++) {
                int difference = Math.abs((baseline[i] & 255) - (optimized[i] & 255));
                if (difference != 0) channels++;
                maxDelta = Math.max(maxDelta, difference);
            }
            Map<String, Object> comparison = new LinkedHashMap<>();
            comparison.put("nativeStableAcrossComparison", true);
            comparison.put("motionBlurTemporarilyDisabled", true);
            comparison.put("motionBlurRestoredEnabledState", previousBlur);
            comparison.put("differentRgbaChannels", channels); comparison.put("maximumChannelDifference", maxDelta);
            comparison.put("comparedPixels", client.width * client.height);
            comparison.put("width", client.width); comparison.put("height", client.height);
            comparison.put("scene", "Same paused seed-71 world, same player camera, partial tick zero");
            result.put("terrainPixelParity", comparison);
            save(client, "040-1710-terrain-native.png", baseline);
            save(client, "040-1710-terrain-batched.png", optimized);
            Map<String, Object> sensitivity = subpixelReference(client, nativePath, baseline, optimized);
            comparison.put("nativeSubpixelRasterSensitivity", sensitivity);
            boolean equivalent = ((Number) sensitivity.get("batchedAlphaDifferences")).intValue() == 0
                && ((Number) sensitivity.get("batchedPixelsOutsideQuantizedNativeEnvelope")).intValue() == 0
                && ((Number) sensitivity.get("batchedChannelsOutsideQuantizedNativeEnvelope")).intValue() == 0
                && ((Number) sensitivity.get("batchedChangedPixels")).intValue() <= ((Number) sensitivity.get("largestNativePerturbationChangedPixels")).intValue()
                && ((Number) sensitivity.get("batchedRgbRmse")).doubleValue() <= ((Number) sensitivity.get("largestNativePerturbationRgbRmse")).doubleValue()
                && maxDelta <= ((Number) sensitivity.get("largestNativePerturbationChannelDelta")).intValue();
            comparison.put("bitwiseRgbaIdentical", channels == 0);
            comparison.put("rasterEquivalentWithinNativeFloatEnvelope", equivalent);
            comparison.put("qualityGate", "Exact alpha; every RGB channel inside eight native camera controls translated by +/-1/8192 block per axis, with one 8-bit RGB output level allowed for quantization; changed-pixel count, maximum channel delta and RGB RMSE must not exceed the native controls");
            require(equivalent, "Terrain batching exceeded the measured native floating-point raster envelope");
            for (int i = 0; i < 40; i++) { frame(client, nativePath, true); frame(client, nativePath, false); }
            long firstDraws = ((Number) stat(terrain.getName(), "drawCalls")).longValue();
            long firstNative = ((Number) stat(terrain.getName(), "nativeBatches")).longValue();
            List<Long> nativeTimes = new ArrayList<>(), batchedTimes = new ArrayList<>();
            for (int round = 0; round < 4; round++) for (int sample = 0; sample < 32; sample++) {
                boolean useNative = (round & 1) == 0;
                long start = System.nanoTime();
                frame(client, nativePath, useNative);
                long elapsed = System.nanoTime() - start;
                (useNative ? nativeTimes : batchedTimes).add(elapsed);
            }
            long draws = ((Number) stat(terrain.getName(), "drawCalls")).longValue() - firstDraws;
            long nativeBatches = ((Number) stat(terrain.getName(), "nativeBatches")).longValue() - firstNative;
            Map<String, Object> timing = new LinkedHashMap<>();
            timing.put("measurementScope", "Fixed-camera world renderer, synchronized GPU samples; excludes GUI, simulation and motion post-processing");
            timing.put("samplingOrder", List.of("native", "batched", "native", "batched"));
            timing.put("native", summarize(nativeTimes)); timing.put("batched", summarize(batchedTimes));
            timing.put("batchedDrawSubmissions", draws); timing.put("nativeBatches", nativeBatches);
            timing.put("profilingEnabled", Boolean.getBoolean("arcadium.legacyPerformance.profileTerrain") || Boolean.getBoolean("arcadium.featureTest1710.benchmark"));
            result.put("pairedRendererPerformance", timing);
            require(draws > 0 && nativeBatches > 0, "The paired benchmark did not exercise both submission paths; draws=" + draws + ", native=" + nativeBatches + ", profiling=" + timing.get("profilingEnabled"));
            complete = true;
        } catch (Throwable failure) {
            call(blur, "setEnabled", previousBlur);
            throw failure;
        } finally {
            nativePath.setBoolean(null, priorNative);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL20.glUseProgram(program); GL13.glActiveTexture(texture); GL13.glClientActiveTexture(clientTexture);
            GL11.glMatrixMode(matrix);
            client.getRenderTarget().bindWrite(true);
        }
        if (complete) { call(blur, "setEnabled", previousBlur); client.openScreen(previous); }
        return complete;
    }

    private static Map<String, Object> subpixelReference(Minecraft client, Field nativePath, byte[] baseline, byte[] optimized) throws Exception {
        var camera = client.camera;
        double[] prior = {camera.x, camera.y, camera.z, camera.lastX, camera.lastY, camera.lastZ, camera.prevX, camera.prevY, camera.prevZ};
        double step = 1.0 / 8192.0;
        byte[] minimum = baseline.clone(), maximum = baseline.clone();
        int maximumPixels = 0, maximumDelta = 0;
        double largestSquared = 0;
        try {
            for (int corner = 0; corner < 8; corner++) {
                double dx = (corner & 1) == 0 ? -step : step;
                double dy = (corner & 2) == 0 ? -step : step;
                double dz = (corner & 4) == 0 ? -step : step;
                camera.x = prior[0] + dx; camera.y = prior[1] + dy; camera.z = prior[2] + dz;
                camera.lastX = prior[3] + dx; camera.lastY = prior[4] + dy; camera.lastZ = prior[5] + dz;
                camera.prevX = prior[6] + dx; camera.prevY = prior[7] + dy; camera.prevZ = prior[8] + dz;
                byte[] shifted = pixels(client, nativePath, true);
                int changed = 0; double squared = 0;
                for (int pixel = 0; pixel < shifted.length; pixel += 4) {
                    boolean differs = false;
                    for (int channel = 0; channel < 4; channel++) {
                        int index = pixel + channel, value = shifted[index] & 255, reference = baseline[index] & 255;
                        minimum[index] = (byte) Math.min(minimum[index] & 255, value);
                        maximum[index] = (byte) Math.max(maximum[index] & 255, value);
                        if (channel < 3) { int delta = Math.abs(value - reference); differs |= delta != 0; squared += delta * delta; maximumDelta = Math.max(maximumDelta, delta); }
                    }
                    if (differs) changed++;
                }
                maximumPixels = Math.max(maximumPixels, changed); largestSquared = Math.max(largestSquared, squared);
                if (corner == 0) save(client, "040-1710-terrain-native-subpixel.png", shifted);
            }
        } finally {
            camera.x = prior[0]; camera.y = prior[1]; camera.z = prior[2];
            camera.lastX = prior[3]; camera.lastY = prior[4]; camera.lastZ = prior[5];
            camera.prevX = prior[6]; camera.prevY = prior[7]; camera.prevZ = prior[8];
        }
        byte[] repeated = pixels(client, nativePath, true);
        require(Arrays.equals(baseline, repeated), "Restoring the subpixel camera did not reproduce the original native frame");
        int outsidePixels = 0, outsideChannels = 0, maximumExcursion = 0, alphaDifferences = 0, changedPixels = 0;
        int quantizedOutsidePixels = 0, quantizedOutsideChannels = 0;
        double squared = 0;
        for (int pixel = 0; pixel < optimized.length; pixel += 4) {
            boolean outside = false, changed = false, quantizedOutside = false;
            for (int channel = 0; channel < 4; channel++) {
                int index = pixel + channel, value = optimized[index] & 255, original = baseline[index] & 255;
                if (channel == 3) { if (value != original) alphaDifferences++; continue; }
                int delta = value - original; squared += delta * delta; changed |= delta != 0;
                int excursion = Math.max((minimum[index] & 255) - value, value - (maximum[index] & 255));
                if (excursion > 0) { outside = true; outsideChannels++; maximumExcursion = Math.max(maximumExcursion, excursion); }
                if (excursion > 1) { quantizedOutside = true; quantizedOutsideChannels++; }
            }
            if (outside) outsidePixels++;
            if (quantizedOutside) quantizedOutsidePixels++;
            if (changed) changedPixels++;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("cameraStepBlocks", step); evidence.put("stepDefinition", "Native camera translated by +/-1/8192 block per axis; graphics settings unchanged");
        evidence.put("nativePerturbations", 8); evidence.put("nativeRoundTripExact", true);
        evidence.put("largestNativePerturbationChangedPixels", maximumPixels); evidence.put("largestNativePerturbationChannelDelta", maximumDelta);
        evidence.put("largestNativePerturbationRgbRmse", Math.sqrt(largestSquared / (baseline.length / 4 * 3.)));
        evidence.put("batchedChangedPixels", changedPixels); evidence.put("batchedRgbRmse", Math.sqrt(squared / (baseline.length / 4 * 3.)));
        evidence.put("batchedAlphaDifferences", alphaDifferences); evidence.put("batchedPixelsOutsideNativeSubpixelEnvelope", outsidePixels);
        evidence.put("batchedChannelsOutsideNativeSubpixelEnvelope", outsideChannels); evidence.put("maximumOutsideEnvelopeExcursion", maximumExcursion);
        evidence.put("rgbOutputQuantizationAllowanceLevels", 1);
        evidence.put("batchedPixelsOutsideQuantizedNativeEnvelope", quantizedOutsidePixels);
        evidence.put("batchedChannelsOutsideQuantizedNativeEnvelope", quantizedOutsideChannels);
        return evidence;
    }

    private static void frame(Minecraft client, Field nativePath, boolean useNative) throws Exception {
        nativePath.setBoolean(null, useNative);
        client.getRenderTarget().bindWrite(true);
        client.gameRenderer.renderWorld(0f, 0L);
        GL11.glFinish();
    }

    private static byte[] pixels(Minecraft client, Field nativePath, boolean useNative) throws Exception {
        frame(client, nativePath, useNative);
        ByteBuffer data = ByteBuffer.allocateDirect(client.width * client.height * 4);
        GL11.glReadPixels(0, 0, client.width, client.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        byte[] pixels = new byte[data.remaining()]; data.get(pixels); return pixels;
    }

    private static Map<String, Object> summarize(List<Long> values) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        double average = Arrays.stream(sorted).average().orElseThrow();
        return Map.of("frames", sorted.length, "meanFrameMillis", average / 1_000_000,
            "medianFrameMillis", sorted[sorted.length / 2] / 1_000_000., "p95FrameMillis", sorted[(int) Math.floor((sorted.length - 1) * .95)] / 1_000_000.,
            "meanRendererFps", 1_000_000_000. / average);
    }

    private static void save(Minecraft client, String name, byte[] data) throws Exception {
        BufferedImage image = new BufferedImage(client.width, client.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < client.height; y++) for (int x = 0; x < client.width; x++) {
            int at = (y * client.width + x) * 4;
            image.setRGB(x, client.height - 1 - y, (data[at + 3] & 255) << 24 | (data[at] & 255) << 16 | (data[at + 1] & 255) << 8 | data[at + 2] & 255);
        }
        ImageIO.write(image, "PNG", client.gameDir.toPath().resolve("screenshots").resolve(name).toFile());
    }

    private static final class PausedScene extends Screen {
        @Override public void render(int mouseX, int mouseY, float delta) { }
        @Override public boolean shouldPauseGame() { return true; }
    }
}
