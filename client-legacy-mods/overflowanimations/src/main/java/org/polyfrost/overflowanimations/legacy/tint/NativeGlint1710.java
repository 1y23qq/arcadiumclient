/**
 * OverflowAnimations
 * The all-you-could-want legacy animations mod for modern minecraft versions.
 * Brings back animations from the 1.7/1.8 era and more.
 * <p>
 * Copyright (C) 2024-2027 lowercasebtw
 * Copyright (C) 2024-2027 mixces
 * Copyright (C) 2024-2027 Contributors to the project retain their copyright
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * "MINECRAFT" LINKING EXCEPTION TO THE GPL
 */

package org.polyfrost.overflowanimations.legacy.tint;

//? if =1.7.10 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.vertex.LegacyTesselatorBackend;
import net.minecraft.resource.Identifier;
import org.polyfrost.overflowanimations.config.OverflowAnimationsConfig;
import org.polyfrost.overflowanimations.util.enums.ItemGlintSetting;
import org.lwjgl.opengl.GL11;

public final class NativeGlint1710 {
    private NativeGlint1710() { }

    public static void gui(int x, int y, int width, int height, float z) {
        Minecraft.getInstance().getTextureManager().bind(LegacyGlint.MODERN_ITEM_TEXTURE);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        try {
            GL11.glLoadIdentity();
            LegacyGlint.setupModernTexturing(1, LegacyGlint.modernSpeed());
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            LegacyTesselatorBackend vertices = LegacyTesselatorBackend.INSTANCE;
            vertices.begin();
            vertices.vertex(x, y + height, z, 0, 1);
            vertices.vertex(x + width, y + height, z, 1, 1);
            vertices.vertex(x + width, y, z, 1, 0);
            vertices.vertex(x, y, z, 0, 0);
            vertices.end();
        } finally {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    public static void flat(Identifier texture) {
        boolean modern = OverflowAnimationsConfig.instance().items.itemGlint == ItemGlintSetting.V1_15;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        try {
            Minecraft.getInstance().getTextureManager().bind(modern ? LegacyGlint.MODERN_ITEM_TEXTURE : texture);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
            GL11.glDepthFunc(GL11.GL_EQUAL);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            float strength = modern ? LegacyGlint.modernStrength() : 0.76F;
            GL11.glColor4f(modern ? strength : 0.5F * strength, modern ? strength : 0.25F * strength, modern ? strength : 0.8F * strength, 1);
            for (int layer = 0; layer < (modern ? 1 : 2); layer++) {
                GL11.glMatrixMode(GL11.GL_TEXTURE);
                GL11.glLoadIdentity();
                if (modern) LegacyGlint.setupModernTexturing(0.5F, LegacyGlint.modernSpeed());
                else LegacyGlint.setupItemTexturing(layer);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                var vertices = LegacyTesselatorBackend.INSTANCE;
                vertices.begin();
                vertices.vertex(-0.5, -0.25, 0, 0, 1);
                vertices.vertex(0.5, -0.25, 0, 1, 1);
                vertices.vertex(0.5, 0.75, 0, 1, 0);
                vertices.vertex(-0.5, 0.75, 0, 0, 0);
                vertices.end();
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
        }
    }

    public static void hand(LegacyTesselatorBackend vertices, float thickness) {
        float strength = LegacyGlint.modernStrength();
        Minecraft.getInstance().getTextureManager().bind(LegacyGlint.MODERN_ITEM_TEXTURE);
        GL11.glColor4f(strength, strength, strength, 1);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        try {
            GL11.glLoadIdentity();
            LegacyGlint.setupModernTexturing(0.5F, LegacyGlint.modernSpeed());
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            ItemInHandRenderer.draw(vertices, 0, 0, 1, 1, 256, 256, thickness);
        } finally {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_TEXTURE);
        }
    }
}

//?}
