// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.background;

import io.github.arcadiumclient.runtime.client.MenuPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.SimpleTexture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.Resource;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public final class MenuPanoramaRenderer {
    private static final Identifier[] SLOTS = new Identifier[6];
    private static String loaded;
    private static boolean loadedResourcePack;

    public static void invalidateResources() { loaded = null; }

    public static boolean hasResourcePackPanorama() {
        for (int i = 0; i < 6; i++) {
            try {
                var resources = Minecraft.getInstance().getResourceManager().getResources(new Identifier(PanoramaCatalog.resourcePackFace(i)));
                boolean custom = resources.size() > 1;
                for (Object resource : resources) ((Resource) resource).asStream().close();
                if (custom) return true;
            } catch (java.io.IOException ignored) { }
        }
        return false;
    }

    static {
        for (int i = 0; i < 6; i++) SLOTS[i] = new Identifier("arcadium", "menu-panorama/" + i);
    }

    private MenuPanoramaRenderer() { }

    public static void draw(float ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.width < 1 || minecraft.height < 1) return;
        TextureManager textures = minecraft.getTextureManager();
        PanoramaCatalog.Entry scene = PanoramaCatalog.find(MenuPreferences.getTitlePanorama());
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        double top = .05 * Math.tan(Math.toRadians(70) / 2);
        double right = top * minecraft.width / minecraft.height;
        GL11.glFrustum(-right, right, -top, top, .05, 10);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            boolean useResourcePack = MenuPreferences.getTitleUseResourcePackPanorama();
            if (!scene.id().equals(loaded) || useResourcePack != loadedResourcePack) {
                boolean resourcePack = useResourcePack && hasResourcePackPanorama();
                for (int i = 0; i < 6; i++) {
                    textures.close(SLOTS[i]);
                    textures.register(SLOTS[i], new SimpleTexture(new Identifier(resourcePack ? PanoramaCatalog.resourcePackFace(i) : scene.face(i))));
                }
                loaded = scene.id();
                loadedResourcePack = useResourcePack;
            }
            GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glRotatef(10f + (float) Math.sin(ticks / 600f) * 4f, 1, 0, 0);
            GL11.glRotatef(ticks * .07f, 0, 1, 0);
            for (int i = 0; i < 6; i++) {
                textures.bind(SLOTS[i]);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glPushMatrix();
                if (i < 4) GL11.glRotatef(-90f * i, 0, 1, 0);
                else GL11.glRotatef(i == 4 ? 90f : -90f, 1, 0, 0);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0, 0); GL11.glVertex3f(-1, 1, -1);
                GL11.glTexCoord2f(0, 1); GL11.glVertex3f(-1, -1, -1);
                GL11.glTexCoord2f(1, 1); GL11.glVertex3f(1, -1, -1);
                GL11.glTexCoord2f(1, 0); GL11.glVertex3f(1, 1, -1);
                GL11.glEnd();
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);
            GL11.glPopAttrib();
            GL20.glUseProgram(program);
            GlStateManager.bindTexture(0);
            GlStateManager.bindTexture(previousTexture);
            GlStateManager.activeTexture(active);
        }
    }
}
