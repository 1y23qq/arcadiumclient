// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

final class LegacyFontParity040Qa {
    private static final int WIDTH = 512;
    private static final int HEIGHT = 256;

    private LegacyFontParity040Qa() {}

    static void verify(Minecraft minecraft, Map<String, Object> result) throws Exception {
        require(FabricLoader.getInstance().isModLoaded("arcadium-legacy-performance"), "The 1.7.10 performance component was not installed");
        require(GLX.useFbo(), "Font parity requires framebuffer objects");
        Class<?> performance = Class.forName("io.github.arcadiumclient.performance.LegacyPerformance");
        Field enabled = performance.getDeclaredField("fontBatchEnabled");
        enabled.setAccessible(true);
        Field batchField = minecraft.textRenderer.getClass().getDeclaredField("arcadium$glyphBatch");
        batchField.setAccessible(true);
        Object batch = batchField.get(minecraft.textRenderer);
        long previousGlyphs = ((Number) batch.getClass().getMethod("submittedGlyphs").invoke(batch)).longValue();
        long previousSubmissions = ((Number) batch.getClass().getMethod("submissions").invoke(batch)).longValue();
        boolean previousEnabled = enabled.getBoolean(null);
        boolean unicode = minecraft.textRenderer.getUnicode();
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousMatrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        RenderTarget target = null;
        try {
            GL20.glUseProgram(0);
            target = new RenderTarget(WIDTH, HEIGHT, false);
            target.bindWrite(true);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            enabled.setBoolean(null, false);
            byte[] baseline = capture(minecraft.textRenderer);
            enabled.setBoolean(null, true);
            byte[] optimized = capture(minecraft.textRenderer);
            require(Arrays.equals(baseline, optimized), "Batched glyphs changed pixels for formatting, shadows, Unicode or decorations");
            int nonBackgroundPixels = 0;
            for (int pixel = 0; pixel < WIDTH * HEIGHT; pixel++) {
                boolean changed = false;
                for (int channel = 0; channel < 4; channel++) changed |= baseline[pixel * 4 + channel] != baseline[channel];
                if (changed) nonBackgroundPixels++;
            }
            require(nonBackgroundPixels > 1000, "The font framebuffer contained no meaningful rendered glyph coverage");
            long glyphs = ((Number) batch.getClass().getMethod("submittedGlyphs").invoke(batch)).longValue() - previousGlyphs;
            long submissions = ((Number) batch.getClass().getMethod("submissions").invoke(batch)).longValue() - previousSubmissions;
            require(glyphs > 100 && submissions > 0 && submissions < glyphs / 2,
                    "The real game glyph batch did not reduce draw submission count");
            Map<String, Object> checks = new LinkedHashMap<>();
            checks.put("identicalRgbaPixels", WIDTH * HEIGHT);
            checks.put("nonBackgroundPixels", nonBackgroundPixels);
            checks.put("optimizedGlyphs", glyphs);
            checks.put("optimizedDrawSubmissions", submissions);
            checks.put("plainFormattingShadowUnicodeBoldItalicUnderlineStrikethrough", true);
            checks.put("exactGeometryAndColors", true);
            result.put("fontPixelParity", checks);
        } finally {
            enabled.setBoolean(null, previousEnabled);
            minecraft.textRenderer.setUnicode(unicode);
            if (target != null) target.destroyBuffers();
            GLX.bindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrix);
            GL11.glPopAttrib();
            GL20.glUseProgram(previousProgram);
        }
    }

    private static byte[] capture(TextRenderer font) {
        GL11.glClearColor(0.125f, 0.25f, 0.5f, 0.75f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        font.setUnicode(false);
        font.draw("Arcadium 1.7.10: exact vanilla font geometry.", 8, 8, 0xFFFFFFFF);
        font.drawWithShadow("Shadows, §aGreen §bCyan §eYellow §fWhite §rReset", 8, 28, 0xFFE5A4C1);
        font.draw("§lBold §oItalic §rNormal §nUnderline §rNormal §mStrike", 8, 48, 0xFF4CA8F2);
        font.drawWithShadow("§l§o§nStyled §rBaseline §c§mRed strike §rEnd", 8, 68, 0xA0EAD8BF);
        font.draw("ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789", 8, 88, 0xCC3AA0FF);
        font.draw("abcdefghijklmnopqrstuvwxyz !@#$%^&*()-_=+[]{}", 8, 108, 0xFF36F1B0);
        font.setUnicode(true);
        font.draw("Unicode: 日本語 中文 Ελληνικά Кириллица", 8, 132, 0xFFF4EADC);
        font.drawWithShadow("§lUnicode bold §oItalic §rABC 日本語 123", 8, 154, 0xFFC0DEF8);
        font.setUnicode(false);
        GL11.glFinish();
        ByteBuffer pixels = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
        GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] output = new byte[pixels.remaining()];
        pixels.get(output);
        return output;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
