// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.BufferUtils;

public final class CustomCrosshairRenderer {
    private static Field firstOpacity;
    private static Field thirdOpacity;
    private static Field disabled, hideContainers, hidePerspective, showSpectator;
    private static boolean lookedUp;
    private static int rendered;
    private static final FloatBuffer COLOR = BufferUtils.createFloatBuffer(4);
    private static Window cachedWindow;
    private static int cachedWidth, cachedHeight, cachedScale;
    private static boolean cachedUnicode;

    private CustomCrosshairRenderer() {}

    public static boolean render() {
        if (!CustomCrosshair.isActive()) return false;
        Minecraft mc = Minecraft.getInstance();
        Window window = window(mc);
        CrosshairDesign design = CustomCrosshair.design();
        float opacity = design.opacity * opacity(mc.options.perspective == 0);
        if (opacity <= 0) return true;
        float scale = design.scale * GuiScaling.crosshairRatio(window);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        COLOR.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR);
        GlStateManager.pushMatrix();
        GlStateManager.translated(window.getScaledWidth() / 2, window.getScaledHeight() / 2, 0);
        GlStateManager.scalef(scale, scale, 1);
        GlStateManager.disableTexture();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableCull();
        GlStateManager.disableDepthTest();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 771);
        try {
            BufferBuilder b = Tesselator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
            List<CrosshairDesign.Quad> quads = CustomCrosshair.mesh().quads();
            for (int i = 0; i < quads.size(); i++) {
                CrosshairDesign.Quad q = quads.get(i);
                int color = q.color(), a = Math.round((color >>> 24) * opacity);
                vertex(b, q.x1(), q.y1(), color, a);
                vertex(b, q.x2(), q.y2(), color, a);
                vertex(b, q.x3(), q.y3(), color, a);
                vertex(b, q.x4(), q.y4(), color, a);
            }
            Tesselator.getInstance().end();
            rendered++;
        } finally {
            if (texture) GlStateManager.enableTexture();
            if (alpha) GlStateManager.enableAlphaTest();
            if (cull) GlStateManager.enableCull();
            if (depth) GlStateManager.enableDepthTest();
            GlStateManager.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blend) GlStateManager.disableBlend();
            GlStateManager.clearColor();
            GlStateManager.color4f(COLOR.get(0), COLOR.get(1), COLOR.get(2), COLOR.get(3));
            GlStateManager.popMatrix();
        }
        return true;
    }

    private static void vertex(BufferBuilder b, float x, float y, int color, int alpha) {
        b.vertex(x, y, 0).color(color >> 16 & 255, color >> 8 & 255, color & 255, alpha).nextVertex();
    }

    private static float opacity(boolean firstPerson) {
        lookup();
        try {
            Field field = firstPerson ? firstOpacity : thirdOpacity;
            return field == null ? 1 : Math.max(0, Math.min(1, field.getFloat(null) / 100f));
        } catch (IllegalAccessException ignored) { return 1; }
    }

    public static boolean visible(boolean vanilla) {
        lookup();
        Minecraft mc = Minecraft.getInstance();
        if (flag(disabled) || (mc.screen != null && flag(hideContainers))
                || (mc.options.perspective != 0 && flag(hidePerspective))) return false;
        return vanilla || (mc.interactionManager != null && mc.interactionManager.isSpectator() && flag(showSpectator));
    }

    private static boolean flag(Field field) {
        try { return field != null && field.getBoolean(null); }
        catch (IllegalAccessException ignored) { return false; }
    }

    private static void lookup() {
        if (!lookedUp) {
            lookedUp = true;
            try {
                Class<?> config = Class.forName("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig");
                firstOpacity = config.getField("crosshairOpacityFirstPerson");
                thirdOpacity = config.getField("crosshairOpacityThirdPerson");
                disabled = config.getField("disableCrosshair");
                hideContainers = config.getField("hideCrosshairInContainers");
                hidePerspective = config.getField("hideCrosshairInPerspective");
                showSpectator = config.getField("showCrosshairInSpectator");
            } catch (ReflectiveOperationException ignored) { }
        }
    }

    private static Window window(Minecraft mc) {
        boolean unicode = mc.isUnicode();
        if (cachedWindow == null || cachedWidth != mc.width || cachedHeight != mc.height
                || cachedScale != mc.options.guiScale || cachedUnicode != unicode) {
            cachedWidth = mc.width; cachedHeight = mc.height; cachedScale = mc.options.guiScale; cachedUnicode = unicode;
            cachedWindow = new Window(mc);
        }
        return cachedWindow;
    }

    public static int renderedCount() { return rendered; }
}
