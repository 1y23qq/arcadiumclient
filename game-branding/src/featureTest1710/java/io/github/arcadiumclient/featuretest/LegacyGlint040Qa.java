// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

public final class LegacyGlint040Qa {
    private static final int SIZE = 128;
    private static boolean collecting;
    private static final List<Map<String,Object>> GUI_CALLS = new ArrayList<>();
    private LegacyGlint040Qa() {}

    static void verify(Minecraft client, Map<String, Object> result) throws Exception {
        Object config = instance("org.polyfrost.glintcolorizer.config.GlintConfig");
        Object options = call(config, "getGuiItem");
        Object color = call(options, "getGlintColor");
        boolean enabled = (boolean) call(config, "getEnabled");
        boolean individual = (boolean) call(options, "getIndividualStrokes");
        int argb = ((Number) call(color, "getRawArgb")).intValue();
        float speed = ((Number) call(options, "getSpeed")).floatValue();
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE), program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        RenderTarget target = null;
        try {
            call(config, "setEnabled", true); call(options, "setIndividualStrokes", false); call(options, "setSpeed", 0f);
            GUI_CALLS.clear(); collecting = true;
            GL20.glUseProgram(0);
            target = new RenderTarget(SIZE, SIZE, true); target.bindWrite(true);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, 32, 32, 0, -1000, 1000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true); GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL); GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            ItemRenderer renderer = new ItemRenderer();
            ItemStack item = new ItemStack(Items.DIAMOND_SWORD); item.addEnchantment(Enchantment.SHARPNESS, 1);
            call(config, "setEnabled", false);
            byte[] vanilla = capture(client, renderer, item);
            call(config, "setEnabled", true);
            call(color, "setArgb", 0xFFFF0000);
            Object redArgb = call(color, "getArgb");
            byte[] red = capture(client, renderer, item);
            call(color, "setArgb", 0xFF00FF00);
            Object greenArgb = call(color, "getArgb");
            byte[] green = capture(client, renderer, item);
            int changes = 0, colored = 0;
            for (int pixel = 0; pixel < SIZE * SIZE; pixel++) {
                int offset = pixel * 4;
                int redDifference = (red[offset] & 255) - (green[offset] & 255);
                int greenDifference = (green[offset + 1] & 255) - (red[offset + 1] & 255);
                if (redDifference > 3 && greenDifference > 3) changes++;
                if ((red[offset] & 255) + (red[offset + 1] & 255) + (red[offset + 2] & 255) > 20) colored++;
            }
            save(client, red, "040-1710-glint-red.png"); save(client, green, "040-1710-glint-green.png");
            save(client, vanilla, "040-1710-glint-vanilla.png");
            result.put("nativeGlintDiagnostics", Map.of("actualGuiCalls", GUI_CALLS, "redArgb", redArgb, "greenArgb", greenArgb,
                "enchantmentGlint", item.hasEnchantmentGlint(), "vanillaVersusRedPixels", different(vanilla, red), "redVersusGreenPixels", different(red, green)));
            require(GUI_CALLS.size() >= 2, "The actual decorated GUI entry did not reach both native glint draws");
            require(changes > 10 && colored > 200, "The real GUI enchantment glint did not respond to the configured colors: " + changes + "; item coverage=" + colored);
            String helper = "org.polyfrost.glintcolorizer.legacy1710.NativeGlint";
            stat(helper, "enter", item, 0); stat(helper, "enter", item, 1); stat(helper, "exit");
            require(((Number) field(Class.forName(helper), "scope")).intValue() == 0, "Nested glint context did not restore its held-item scope");
            stat(helper, "exit");
            require(((Number) field(Class.forName(helper), "scope")).intValue() == -1, "The GUI glint left a render context active");
            result.put("nativeGlintColors", Map.of("changedPixels", changes, "renderedItemPixels", colored, "nestedContextRestored", true));
        } finally {
            collecting = false;
            call(color, "setArgb", argb); call(options, "setSpeed", speed); call(options, "setIndividualStrokes", individual); call(config, "setEnabled", enabled);
            if (target != null) target.destroyBuffers();
            GLX.bindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(matrix); GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL20.glUseProgram(program);
            GL13.glActiveTexture(activeTexture); GL13.glClientActiveTexture(clientTexture);
        }
    }

    public static void guiEntered(float z) {
        if (!collecting) return;
        Map<String,Object> state = new LinkedHashMap<>();
        state.put("z", z); state.put("depthFunc", GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        state.put("depthTest", GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        state.put("alphaTest", GL11.glIsEnabled(GL11.GL_ALPHA_TEST));
        state.put("texture", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        state.put("activeTexture", GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        state.put("program", GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        GUI_CALLS.add(state);
    }

    private static int different(byte[] first,byte[] second) {
        int count=0;
        for(int i=0;i<first.length;i+=4)if(first[i]!=second[i]||first[i+1]!=second[i+1]||first[i+2]!=second[i+2])count++;
        return count;
    }

    private static byte[] capture(Minecraft client, ItemRenderer renderer, ItemStack item) {
        GL11.glClearColor(0, 0, 0, 1); GL11.glDepthMask(true); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        renderer.renderGuiItemWithEnchantmentGlint(client.textRenderer, client.getTextureManager(), item, 8, 8);
        GL11.glFinish();
        ByteBuffer pixels = ByteBuffer.allocateDirect(SIZE * SIZE * 4);
        GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] data = new byte[pixels.remaining()]; pixels.get(data); return data;
    }

    private static void save(Minecraft client, byte[] data, String name) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int offset = ((SIZE - y - 1) * SIZE + x) * 4;
            image.setRGB(x, y, 0xFF000000 | (data[offset] & 255) << 16 | (data[offset + 1] & 255) << 8 | data[offset + 2] & 255);
        }
        var path = client.gameDir.toPath().resolve("screenshots"); java.nio.file.Files.createDirectories(path);
        ImageIO.write(image, "png", path.resolve(name).toFile());
    }
}
