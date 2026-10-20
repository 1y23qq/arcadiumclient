// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

public final class GlStateManager {
    private GlStateManager() {}
    public static void activeTexture(int value) { GL13.glActiveTexture(value); }
    public static void bindTexture(int value) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, value); }
    public static void blendFunc(int source, int destination) { GL11.glBlendFunc(source, destination); }
    public static void blendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) { GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha); }
    public static void clearColor() { GL11.glColor4f(1f, 1f, 1f, 1f); }
    public static void color4f(float r, float g, float b, float a) { GL11.glColor4f(r, g, b, a); }
    public static void depthMask(boolean value) { GL11.glDepthMask(value); }
    public static void loadIdentity() { GL11.glLoadIdentity(); }
    public static void matrixMode(int value) { GL11.glMatrixMode(value); }
    public static void ortho(double left, double right, double bottom, double top, double near, double far) { GL11.glOrtho(left, right, bottom, top, near, far); }
    public static void popMatrix() { GL11.glPopMatrix(); }
    public static void pushMatrix() { GL11.glPushMatrix(); }
    public static void rotatef(float angle, float x, float y, float z) { GL11.glRotatef(angle, x, y, z); }
    public static void scalef(float x, float y, float z) { GL11.glScalef(x, y, z); }
    public static void translated(double x, double y, double z) { GL11.glTranslated(x, y, z); }
    public static void translatef(float x, float y, float z) { GL11.glTranslatef(x, y, z); }
    public static void enableAlphaTest() { GL11.glEnable(GL11.GL_ALPHA_TEST); }
    public static void disableAlphaTest() { GL11.glDisable(GL11.GL_ALPHA_TEST); }
    public static void enableBlend() { GL11.glEnable(GL11.GL_BLEND); }
    public static void disableBlend() { GL11.glDisable(GL11.GL_BLEND); }
    public static void enableCull() { GL11.glEnable(GL11.GL_CULL_FACE); }
    public static void disableCull() { GL11.glDisable(GL11.GL_CULL_FACE); }
    public static void enableDepthTest() { GL11.glEnable(GL11.GL_DEPTH_TEST); }
    public static void disableDepthTest() { GL11.glDisable(GL11.GL_DEPTH_TEST); }
    public static void enableLighting() { GL11.glEnable(GL11.GL_LIGHTING); }
    public static void disableLighting() { GL11.glDisable(GL11.GL_LIGHTING); }
    public static void enableTexture() { GL11.glEnable(GL11.GL_TEXTURE_2D); }
    public static void disableTexture() { GL11.glDisable(GL11.GL_TEXTURE_2D); }
}
