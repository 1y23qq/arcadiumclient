// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class NativeVisual041Qa {
    private static final String FEATURES = "io.github.arcadiumclient.runtime.client.features.";
    private static final String CROSSHAIR = "io.github.arcadiumclient.runtime.legacy.LegacyCrosshairConfig";
    private static final int SIZE = 64;
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> CASES = new ArrayList<>();
    private static int ticks, draws;
    private static boolean done;

    private NativeVisual041Qa() {}

    public static void tick() {
        if (!Boolean.getBoolean("arcadium.nativeVisual041") || done || ++ticks < 100) return;
        done = true;
        Minecraft client = Minecraft.getInstance();
        Path report = client.gameDir.toPath().resolve("native-visual041-result.json");
        try {
            require("ArcadiumTest".equals(client.getSession().getUsername()) && "0".equals(client.getSession().getAccessToken()), "The visual probe requires its isolated offline account");
            Path actual = client.gameDir.toPath().toRealPath();
            require(actual.equals(Path.of(System.getProperty("arcadium.nativeVisual041.gameDir")).toRealPath()) && actual.startsWith(Path.of("C:/Temp").toRealPath()), "The visual probe escaped its isolated game directory");
            require(!Boolean.getBoolean("arcadium.sharedSettings041") && !Boolean.getBoolean("arcadium.featureTest040"), "The visual probe must not change another fixture's settings");
            RESULT.put("minecraft", "1.7.10");
            RESULT.put("runtimeVersion", FabricLoader.getInstance().getModContainer("arcadium_runtime").orElseThrow().getMetadata().getVersion().getFriendlyString());
            RESULT.put("scope", "actual-native-vanilla-crosshair-framebuffer-and-gl-state");
            verify(client);
            RESULT.put("passed", true);
        } catch (Throwable failure) {
            failure.printStackTrace(); RESULT.put("passed", false); RESULT.put("error", failure.toString());
        } finally {
            try { Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(RESULT)); }
            catch (Exception failure) { failure.printStackTrace(); }
            client.stop();
        }
    }

    private static void verify(Minecraft client) throws Exception {
        Class<?> config = Class.forName(CROSSHAIR);
        Map<Field, Object> saved = new LinkedHashMap<>();
        for (String name : List.of("disableCrosshair", "hideCrosshairInContainers", "hideCrosshairInPerspective", "removeCrosshairBlendingFirstPerson", "removeCrosshairBlendingThirdPerson", "crosshairOpacityFirstPerson", "crosshairOpacityThirdPerson")) {
            Field option = config.getField(name); saved.put(option, option.get(null));
        }
        Object custom = instance(FEATURES + "CustomCrosshair");
        boolean customEnabled = (boolean) call(custom, "enabled");
        boolean scaling = (boolean) stat(FEATURES + "GuiScaleConfig", "getEnabled");
        int perspective = client.options.perspective;
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE), program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        RenderTarget target = null;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        try {
            for (String name : List.of("disableCrosshair", "hideCrosshairInContainers", "hideCrosshairInPerspective")) config.getField(name).setBoolean(null, false);
            call(custom, "setEnabled", false); stat(FEATURES + "GuiScaleConfig", "setEnabled", false);
            Method wrapper = Arrays.stream(client.gui.getClass().getDeclaredMethods()).filter(method -> method.getName().contains("arcadium$hudTextures") && method.getParameterCount() == 8).findFirst().orElseThrow();
            wrapper.setAccessible(true);
            Class<?> operationType = wrapper.getParameterTypes()[7];
            Object original = Proxy.newProxyInstance(operationType.getClassLoader(), new Class<?>[]{operationType}, (proxy, method, arguments) -> {
                if (!method.getName().equals("call")) throw new UnsupportedOperationException(method.getName());
                Object[] values = (Object[]) arguments[0];
                client.gui.drawTexture((int) values[1], (int) values[2], (int) values[3], (int) values[4], (int) values[5], (int) values[6]);
                draws++; return null;
            });
            RESULT.put("actualMergedWrapper", wrapper.getName());
            GL20.glUseProgram(0);
            target = new RenderTarget(SIZE, SIZE, true); target.bindWrite(true);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, 16, 16, 0, -1000, 1000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            GL11.glColorMask(true, true, true, true); GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST); GL11.glAlphaFunc(GL11.GL_GREATER, .1f); GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD); GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            client.getTextureManager().bind(GuiElement.ICONS_LOCATION);
            settings(config, false, false, 100f, 100f);
            client.options.perspective = 0;
            byte[] vanilla = capture(client, wrapper, original, false, "native-vanilla");
            byte[] defaults = capture(client, wrapper, original, true, "default-fast-path");
            require(Arrays.equals(vanilla, defaults), "Default native crosshair styling changed the original framebuffer bytes");
            RESULT.put("defaultNativeRgbaIdentical", true);

            settings(config, true, false, 100f, 39f);
            byte[] opaque = capture(client, wrapper, original, true, "first-plain-opaque");
            int point = whitePixel(opaque);
            require(point >= 0, "The actual native crosshair atlas did not produce a white foreground pixel");
            settings(config, true, false, 61f, 39f);
            byte[] first = capture(client, wrapper, original, true, "first-plain-61-percent");
            pixel(first, point, new int[]{175, 195, 215, 156}, "First-person plain opacity");
            settings(config, true, false, 0f, 39f);
            byte[] invisible = capture(client, wrapper, original, true, "first-zero-opacity");
            require(background(invisible), "Zero native crosshair opacity still changed framebuffer pixels");
            settings(config, true, false, 61f, 39f);
            client.options.perspective = 1;
            byte[] rear = capture(client, wrapper, original, true, "rear-inverted-39-percent");
            pixel(rear, point, new int[]{204, 153, 102, 99}, "Rear third-person inverted opacity");
            client.options.perspective = 2;
            byte[] front = capture(client, wrapper, original, true, "front-inverted-39-percent");
            require(Arrays.equals(rear, front), "Front and rear third-person styling diverged");
            config.getField("removeCrosshairBlendingThirdPerson").setBoolean(null, true);
            byte[] thirdPlain = capture(client, wrapper, original, true, "front-plain-39-percent");
            pixel(thirdPlain, point, new int[]{131, 162, 193, 99}, "Third-person plain opacity");

            GL14.glBlendFuncSeparate(GL11.GL_DST_ALPHA, GL11.GL_ONE_MINUS_DST_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(.73f, .61f, .49f, .37f);
            Map<String, Object> before = state();
            wrapper.invoke(client.gui, client.gui, 0, 0, 0, 0, 16, 16, original);
            Map<String, Object> after = state();
            require(before.equals(after), "Non-default native crosshair styling leaked caller GL state: " + before + " -> " + after);
            RESULT.put("callerGlStateBefore", before); RESULT.put("callerGlStateAfter", after);
            RESULT.put("callerGlStateRestored", true); RESULT.put("actualOriginalQuadDraws", draws);
            RESULT.put("foregroundPixelIndex", point / 4); RESULT.put("cases", CASES);
            int error = GL11.glGetError(); RESULT.put("glError", error);
            require(error == GL11.GL_NO_ERROR, "The native crosshair probe produced GL error " + error);
        } finally {
            for (var option : saved.entrySet()) option.getKey().set(null, option.getValue());
            call(custom, "setEnabled", customEnabled); stat(FEATURES + "GuiScaleConfig", "setEnabled", scaling);
            client.options.perspective = perspective;
            if (target != null) target.destroyBuffers();
            GLX.bindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(matrix); GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL20.glUseProgram(program);
            GL13.glActiveTexture(activeTexture); GL13.glClientActiveTexture(clientTexture);
        }
    }

    private static void settings(Class<?> config, boolean firstPlain, boolean thirdPlain, float firstOpacity, float thirdOpacity) throws Exception {
        config.getField("removeCrosshairBlendingFirstPerson").setBoolean(null, firstPlain);
        config.getField("removeCrosshairBlendingThirdPerson").setBoolean(null, thirdPlain);
        config.getField("crosshairOpacityFirstPerson").setFloat(null, firstOpacity);
        config.getField("crosshairOpacityThirdPerson").setFloat(null, thirdOpacity);
    }

    private static byte[] capture(Minecraft client, Method wrapper, Object original, boolean styled, String name) throws Exception {
        GL11.glClearColor(.2f, .4f, .6f, 1f); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL14.glBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glColor4f(1, 1, 1, 1);
        if (styled) wrapper.invoke(client.gui, client.gui, 0, 0, 0, 0, 16, 16, original);
        else client.gui.drawTexture(0, 0, 0, 0, 16, 16);
        GL11.glFinish();
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] data = new byte[pixels.remaining()]; pixels.get(data);
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int offset = ((SIZE - y - 1) * SIZE + x) * 4;
            image.setRGB(x, y, (data[offset + 3] & 255) << 24 | (data[offset] & 255) << 16 | (data[offset + 1] & 255) << 8 | data[offset + 2] & 255);
        }
        Path output = client.gameDir.toPath().resolve("screenshots/041-1710-crosshair-" + name + ".png");
        Files.createDirectories(output.getParent()); ImageIO.write(image, "png", output.toFile());
        CASES.add(Map.of("name", name, "perspective", client.options.perspective, "image", output.toString()));
        return data;
    }

    private static int whitePixel(byte[] pixels) {
        for (int i = 0; i < pixels.length; i += 4) if ((pixels[i] & 255) > 250 && (pixels[i + 1] & 255) > 250 && (pixels[i + 2] & 255) > 250 && (pixels[i + 3] & 255) > 250) return i;
        return -1;
    }

    private static void pixel(byte[] data, int point, int[] expected, String name) {
        int[] actual = new int[4];
        for (int channel = 0; channel < 4; channel++) {
            actual[channel] = data[point + channel] & 255;
            require(Math.abs(actual[channel] - expected[channel]) <= 2, name + " channel " + channel + ": expected=" + expected[channel] + ", actual=" + actual[channel]);
        }
        RESULT.put(name, Map.of("expectedRgba", expected, "actualRgba", actual, "allowedOutputLevels", 2));
    }

    private static boolean background(byte[] pixels) {
        for (int i = 0; i < pixels.length; i += 4) if ((pixels[i] & 255) != 51 || (pixels[i + 1] & 255) != 102 || (pixels[i + 2] & 255) != 153 || (pixels[i + 3] & 255) != 255) return false;
        return true;
    }

    private static Map<String, Object> state() {
        FloatBuffer color = BufferUtils.createFloatBuffer(4); GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        return Map.of("color", List.of(color.get(0), color.get(1), color.get(2), color.get(3)), "blend", GL11.glIsEnabled(GL11.GL_BLEND),
            "srcRgb", GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "dstRgb", GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
            "srcAlpha", GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), "dstAlpha", GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
            "texture", GL11.glIsEnabled(GL11.GL_TEXTURE_2D), "alphaTest", GL11.glIsEnabled(GL11.GL_ALPHA_TEST));
    }
}
