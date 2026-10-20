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
        if (!LegacyHeightConfig.getEnabled() || client.world == null || client.player == null
            || NoRender.hide(NoRenderPolicy.Effect.BLOCK_OUTLINE)) return;
        var hit = client.crosshairTarget;
        Integer limit = HeightLimitSource.snapshot().getMaximum();
        if (hit == null || hit.type != HitResult__Type.BLOCK || limit == null || hit.y < limit - 1) return;
        var camera = client.camera;
        if (camera == null) return;
        double x = camera.prevX + (camera.x - camera.prevX) * delta;
        double y = camera.prevY + (camera.y - camera.prevY) * delta;
        double z = camera.prevZ + (camera.z - camera.prevZ) * delta;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-x, -y, -z);
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glLineWidth(2f);
            WorldRenderer.renderOutline(Box.of(hit.x - .002, hit.y - .002, hit.z - .002,
                hit.x + 1.002, hit.y + 1.002, hit.z + 1.002), 0xFF5555);
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
}
