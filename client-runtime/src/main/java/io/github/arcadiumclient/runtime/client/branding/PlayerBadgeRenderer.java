// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.branding;

import io.github.arcadiumclient.runtime.client.presence.ArcadiumPlayersConfig;
import io.github.arcadiumclient.runtime.client.presence.ArcadiumPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class PlayerBadgeRenderer {
    public static final int ADVANCE = 10;
    private static final Identifier ICON = new Identifier("arcadiumclient", "textures/branding/player-badge.png");

    private PlayerBadgeRenderer() { }

    public static boolean inTab(PlayerInfo player) {
        return ArcadiumPlayersConfig.getShowTab() && player != null && player.getProfile() != null
                && player.getProfile().getId() != null && ArcadiumPresence.isOnline(player.getProfile().getId());
    }

    public static boolean onNametag(Entity entity, String label) {
        return label != null && nametagEligible(entity) && label.equals(entity.getDisplayName().getFormattedString());
    }

    public static boolean nametagEligible(Entity entity) {
        return ArcadiumPlayersConfig.getShowNametag() && entity instanceof PlayerEntity && ArcadiumPresence.isOnline(entity.getUuid());
    }

    public static void draw(float x, float y, int textColor) {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        Texture texture = manager.get(ICON);
        if (texture == null) {
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            manager.bind(ICON);
            GlStateManager.bindTexture(previous);
            texture = manager.get(ICON);
            if (texture == null) return;
        }
        float alpha = (textColor >>> 24) / 255f;
        if ((textColor & 0xFC000000) == 0) alpha = 1f;
        // Raw state is isolated so Minecraft's cached state remains valid.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getGlId());
            GL11.glColor4f(1f, 1f, 1f, alpha);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0f, 0f); GL11.glVertex3f(x, y, 0f);
            GL11.glTexCoord2f(0f, 1f); GL11.glVertex3f(x, y + 8f, 0f);
            GL11.glTexCoord2f(1f, 1f); GL11.glVertex3f(x + 8f, y + 8f, 0f);
            GL11.glTexCoord2f(1f, 0f); GL11.glVertex3f(x + 8f, y, 0f);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }
}
