// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import io.github.arcadiumclient.runtime.client.features.HeightLimitSource;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.world.HitResult__Type;
import org.lwjgl.opengl.GL11;

public final class LegacyHeightOverlay {
    private LegacyHeightOverlay() {}
    public static void render(float delta) {
        var client = Minecraft.getInstance();
        if (!LegacyHeightConfig.getHeightOverlay() || client.world == null || client.player == null
            || NoRender.hide(NoRenderPolicy.Effect.BLOCK_OUTLINE)) return;
        float opacity = Math.max(0, Math.min(100, LegacyHeightConfig.getHeightOverlayOpacity())) / 100f;
        if (opacity <= 0) return;
        var hit = client.crosshairTarget;
        if (hit == null || hit.type != HitResult__Type.BLOCK) return;
        int limit = HeightLimitSource.currentLimit();
        if (hit.y < limit - 1) return;
        var camera = client.camera;
        if (camera == null) return;
        double x = camera.prevX + (camera.x - camera.prevX) * delta;
        double y = camera.prevY + (camera.y - camera.prevY) * delta;
        double z = camera.prevZ + (camera.z - camera.prevZ) * delta;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-x, -y, -z);
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false); GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1f, .33333334f, .33333334f, opacity);
            filled(hit.x - .002, hit.y - .002, hit.z - .002, hit.x + 1.002, hit.y + 1.002, hit.z + 1.002);
            GL11.glColor4f(1f, .33333334f, .33333334f, 1f);
            GL11.glLineWidth(2f);
            WorldRenderer.renderOutline(Box.of(hit.x - .002, hit.y - .002, hit.z - .002,
                hit.x + 1.002, hit.y + 1.002, hit.z + 1.002), 0xFF5555);
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }

    private static void filled(double x, double y, double z, double xx, double yy, double zz) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x,y,z); GL11.glVertex3d(xx,y,z); GL11.glVertex3d(xx,y,zz); GL11.glVertex3d(x,y,zz);
        GL11.glVertex3d(x,yy,z); GL11.glVertex3d(x,yy,zz); GL11.glVertex3d(xx,yy,zz); GL11.glVertex3d(xx,yy,z);
        GL11.glVertex3d(x,y,z); GL11.glVertex3d(x,yy,z); GL11.glVertex3d(xx,yy,z); GL11.glVertex3d(xx,y,z);
        GL11.glVertex3d(x,y,zz); GL11.glVertex3d(xx,y,zz); GL11.glVertex3d(xx,yy,zz); GL11.glVertex3d(x,yy,zz);
        GL11.glVertex3d(x,y,z); GL11.glVertex3d(x,y,zz); GL11.glVertex3d(x,yy,zz); GL11.glVertex3d(x,yy,z);
        GL11.glVertex3d(xx,y,z); GL11.glVertex3d(xx,yy,z); GL11.glVertex3d(xx,yy,zz); GL11.glVertex3d(xx,y,zz);
        GL11.glEnd();
    }
}
