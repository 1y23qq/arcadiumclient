// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.emoji;

import java.io.InputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class EmojiPainter {
    private static DynamicTexture atlas;
    private static boolean attempted;
    private EmojiPainter() { }

    public static void draw(int glyph, float x, float y, float size, int color, boolean shadow) {
        int index = EmojiText.index(glyph);
        if (index < 0) return;
        EmojiBatchBridge.beforeImage(Minecraft.getInstance().textRenderer);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            if (!attempted) {
                attempted = true;
                try (InputStream input = EmojiText.class.getResourceAsStream(EmojiText.ATLAS)) {
                    if (input != null) atlas = new DynamicTexture(ImageIO.read(input));
                } catch (Exception failure) {
                    org.slf4j.LoggerFactory.getLogger("Arcadium/Emoji").warn("Could not load emoji atlas", failure);
                }
            }
            if (atlas == null) return;
            GlStateManager.bindTexture(atlas.getGlId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            float alpha = ((color & 0xFC000000) == 0 ? 255 : color >>> 24) / 255f;
            if (shadow) {
                GL11.glColor4f(0, 0, 0, alpha * 0.4f);
                quad(index, x + 1, y + 1, size);
            }
            GL11.glColor4f(1, 1, 1, alpha);
            quad(index, x, y, size);
        } finally {
            GL11.glPopAttrib();
            GlStateManager.bindTexture(binding);
        }
    }

    private static void quad(int index, float x, float y, float size) {
        float u = (index % 8 * 64 + 0.5f) / 512f, v = (index / 8 * 64 + 0.5f) / 512f;
        float extent = 63f / 512f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u, v); GL11.glVertex3f(x, y, 0);
        GL11.glTexCoord2f(u, v + extent); GL11.glVertex3f(x, y + size, 0);
        GL11.glTexCoord2f(u + extent, v + extent); GL11.glVertex3f(x + size, y + size, 0);
        GL11.glTexCoord2f(u + extent, v); GL11.glVertex3f(x + size, y, 0);
        GL11.glEnd();
    }
}
