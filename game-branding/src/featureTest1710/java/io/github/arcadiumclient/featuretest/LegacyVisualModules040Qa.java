// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import io.github.arcadiumclient.featuretest.mixin.LegacyHurtCam040Probe;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

final class LegacyVisualModules040Qa {
    private static boolean complete;
    private LegacyVisualModules040Qa() {}

    static void verify(Minecraft client, Map<String, Object> result) throws Exception {
        if (complete) return;
        Object registry = instance("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry");
        require(call(registry, "findTree", "betterhurtcam.toml") != null, "The real BetterHurtCam settings are absent");
        require(call(registry, "findTree", "sciophobia.json") != null, "The real Sciophobia settings are absent");
        camera(client, result);
        font(client, result);
        complete = true;
    }

    private static void camera(Minecraft client, Map<String, Object> result) throws Exception {
        Class<?> api = Class.forName("net.uku3lig.betterhurtcam.BetterHurtCam");
        Object config = field(Class.forName("net.uku3lig.betterhurtcam.BetterHurtCamConfig"), "INSTANCE");
        Map<String, Object> prior = new LinkedHashMap<>();
        for (String name : List.of("enabled", "multiplier", "type", "oldTypeIn18", "heartBlink")) prior.put(name, field(config, name));
        int timer = client.player.damagedTimer, duration = client.player.damagedTime;
        float direction = client.player.damagedSwingDir;
        var camera = client.camera;
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        try {
            client.camera = client.player; client.player.damagedTimer = 8; client.player.damagedTime = 10; client.player.damagedSwingDir = 45;
            set(config, "enabled", true); set(config, "multiplier", 1d); set(config, "type", "YAW_BASED");
            float[] directional = matrix(client);
            require(!identity(directional), "BetterHurtCam did not render an actual damage rotation");
            set(config, "multiplier", 0d);
            require(identity(matrix(client)), "The actual HurtCam multiplier did not suppress rotation at zero");
            set(config, "multiplier", 1d); set(config, "enabled", false);
            require(identity(matrix(client)), "The actual HurtCam enable control did not suppress rotation");
            set(config, "enabled", true); set(config, "type", "OLD");
            float[] old = matrix(client);
            require(!Arrays.equals(directional, old), "Native OLD and YAW_BASED HurtCam types produced identical matrices");
            KeyBinding[] keys = client.options.keyBindings;
            require(keys != null, "The native keybind array is missing");
            for (String name : List.of("TOGGLE", "INCREASE", "DECREASE")) {
                Object expected = field(api, name); boolean found = false;
                for (KeyBinding key : keys) found |= key == expected;
                require(found, "BetterHurtCam did not register the actual keybind " + name);
            }
            result.put("nativeBetterHurtCam", Map.of("actualCameraMatricesDifferByType", true, "zeroMultiplierAndDisableSuppressRotation", true,
                "nativeF6F7F8BindingsRegistered", true));
        } finally {
            client.camera = camera; client.player.damagedTimer = timer; client.player.damagedTime = duration; client.player.damagedSwingDir = direction;
            for (Map.Entry<String, Object> entry : prior.entrySet()) set(config, entry.getKey(), entry.getValue());
            GL11.glPopMatrix(); GL11.glMatrixMode(matrix);
        }
    }

    private static float[] matrix(Minecraft client) {
        GL11.glLoadIdentity(); ((LegacyHurtCam040Probe) client.gameRenderer).arcadium$hurt(0f);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
        float[] values = new float[16]; buffer.get(values); return values;
    }

    private static boolean identity(float[] values) {
        for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - (i % 5 == 0 ? 1 : 0)) > 0.00001) return false;
        return true;
    }

    private static void font(Minecraft client, Map<String, Object> result) throws Exception {
        Class<?> config = Class.forName("dev.microcontrollers.sciophobia.config.SciophobiaConfig");
        Map<String, Object> prior = new LinkedHashMap<>();
        for (String name : List.of("removeShadows", "forceShadows", "alternateShadows", "fullShadow")) prior.put(name, field(config, name));
        Field batchEnabled = Class.forName("io.github.arcadiumclient.performance.LegacyPerformance").getDeclaredField("fontBatchEnabled");
        batchEnabled.setAccessible(true);
        boolean previousBatch = batchEnabled.getBoolean(null);
        Field batchField = client.textRenderer.getClass().getDeclaredField("arcadium$glyphBatch"); batchField.setAccessible(true);
        Object batch = batchField.get(client.textRenderer);
        long previousSubmissions = ((Number) batch.getClass().getMethod("submissions").invoke(batch)).longValue();
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING), matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int texture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        RenderTarget target = null;
        try {
            GL20.glUseProgram(0); target = new RenderTarget(256, 80, false); target.bindWrite(true);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, 256, 80, 0, -1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (String name : prior.keySet()) set(config, name, false);
            byte[] plain = pairedText(client, false, batchEnabled, "plain"), vanilla = pairedText(client, true, batchEnabled, "default shadow");
            require(pixels(plain) > 20 && pixels(vanilla) > pixels(plain), "Text shadow QA did not draw actual native glyphs and shadow pixels");
            set(config, "removeShadows", true); byte[] hidden = pairedText(client, true, batchEnabled, "hidden shadow");
            require(Arrays.equals(plain, hidden), "Hide Text Shadow did not remove the actual native shadow");
            set(config, "removeShadows", false); set(config, "forceShadows", true); byte[] forced = pairedText(client, false, batchEnabled, "forced shadow");
            require(Arrays.equals(vanilla, forced), "Force Text Shadow did not match actual native shadow pixels");
            set(config, "forceShadows", false); set(config, "alternateShadows", true); byte[] alternate = pairedText(client, true, batchEnabled, "alternate shadow");
            require(!Arrays.equals(vanilla, alternate), "Alternate Text Shadow did not move actual shadow pixels");
            set(config, "alternateShadows", false); set(config, "fullShadow", true); byte[] full = pairedText(client, true, batchEnabled, "full shadow");
            require(pixels(full) > pixels(vanilla), "Full Text Shadow did not add an actual surrounding outline");
            long submissions = ((Number) batch.getClass().getMethod("submissions").invoke(batch)).longValue() - previousSubmissions;
            require(submissions > 6, "Sciophobia shadow passes bypassed the actual glyph batch");
            result.put("nativeSciophobia", Map.of("plainPixels", pixels(plain), "vanillaShadowPixels", pixels(vanilla),
                "fullShadowPixels", pixels(full), "hideAndForceExactPixelParity", true, "alternatePixelsDiffer", true,
                "nativeAndBatchedRgbaIdenticalAcrossSixCases", true, "batchedSubmissions", submissions));
        } finally {
            batchEnabled.setBoolean(null, previousBatch);
            for (Map.Entry<String, Object> entry : prior.entrySet()) set(config, entry.getKey(), entry.getValue());
            if (target != null) target.destroyBuffers(); GLX.bindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL20.glUseProgram(program); GL11.glMatrixMode(matrix);
            GL13.glActiveTexture(texture); GL13.glClientActiveTexture(clientTexture);
        }
    }

    private static byte[] pairedText(Minecraft client, boolean shadow, Field enabled, String mode) throws Exception {
        enabled.setBoolean(null, false); byte[] nativePixels = text(client, shadow);
        enabled.setBoolean(null, true); byte[] batched = text(client, shadow);
        require(Arrays.equals(nativePixels, batched), "The glyph batch changed actual Sciophobia pixels for " + mode);
        return batched;
    }

    private static byte[] text(Minecraft client, boolean shadow) {
        GL11.glClearColor(0, 0, 0, 0); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        if (shadow) client.textRenderer.drawWithShadow("Shadow QA", 20, 20, 0xFFFFFFFF);
        else client.textRenderer.draw("Shadow QA", 20, 20, 0xFFFFFFFF);
        GL11.glFinish(); ByteBuffer bytes = BufferUtils.createByteBuffer(256 * 80 * 4);
        GL11.glReadPixels(0, 0, 256, 80, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
        byte[] output = new byte[bytes.remaining()]; bytes.get(output); return output;
    }

    private static int pixels(byte[] data) { int count = 0; for (int i = 3; i < data.length; i += 4) if (data[i] != 0) count++; return count; }

    private static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target instanceof Class<?> owner ? owner : target.getClass(); Field field = type.getDeclaredField(name); field.setAccessible(true);
        field.set(target instanceof Class<?> ? null : target, value);
    }
}
