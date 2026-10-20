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
import net.minecraft.entity.living.LivingEntity;
import org.lwjgl.opengl.GL11;
import org.polyfrost.overflowanimations.util.enums.DamageTintSetting;

public final class NativeDamageTint1710 {
    private static int depth;
    private static final int[] COLORS = new int[8];
    private NativeDamageTint1710() { }
    public static boolean active() { return depth != 0; }

    public static boolean begin(LivingEntity entity) {
        if (depth == COLORS.length || !LegacyDamageTint.isHurt(entity) || entity.isInvisible()) return false;
        DamageTintSetting style = LegacyDamageTint.config().damageTintStyle;
        if (style == DamageTintSetting.VANILLA) style = DamageTintSetting.V1_7;
        int color = style.getColor(entity.getBrightness(1));
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        Minecraft.getInstance().gameRenderer.disableLightMap(0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthFunc(GL11.GL_EQUAL);
        GL11.glDepthMask(false);
        GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, 1 - (color >>> 24) / 255F);
        COLORS[depth++] = color;
        return true;
    }

    public static void color() {
        int value = COLORS[depth - 1];
        GL11.glColor4f((value >> 16 & 255) / 255F, (value >> 8 & 255) / 255F, (value & 255) / 255F, 1 - (value >>> 24) / 255F);
    }

    public static void end() {
        depth--;
        GL11.glPopAttrib();
    }
}

//?}
