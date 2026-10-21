// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from OverflowAnimations. Copyright (c) 2024-2027 lowercasebtw, mixces and contributors.
package io.github.arcadiumclient.forge1710.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.forge1710.platform.ForgeFrameState;
import io.github.arcadiumclient.runtime.client.features.CustomCrosshairRenderer;
import io.github.arcadiumclient.runtime.client.features.LegacyGuiScaleTransform;
import io.github.arcadiumclient.runtime.legacy.LegacyWindows;
import io.github.arcadiumclient.runtime.legacy.LegacyCrosshairConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.polyfrost.overflowanimations.config.OverflowAnimationsConfig;
import org.polyfrost.overflowanimations.legacy.hud.DebugCrosshair;
import org.polyfrost.overflowanimations.util.enums.DebugCrosshairSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

@Mixin(targets = "net.minecraftforge.client.GuiIngameForge")
public abstract class ForgeAnimationsHudMixin {
    @WrapOperation(method = "renderCrosshairs(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/GuiIngameForge;func_73729_b(IIIIII)V"), remap = false)
    private void arcadium$crosshair(@Coerce Object gui, int x, int y, int u, int v, int width, int height, Operation<Void> original) {
        Minecraft mc = Minecraft.func_71410_x();
        var settings = OverflowAnimationsConfig.instance().screen;
        if (!settings.thirdPersonCrosshair.isLegacy() && mc.field_71474_y.field_74320_O != 0 && !CustomCrosshairRenderer.showInThirdPerson()) return;
        if (CustomCrosshairRenderer.render()) return;
        if (settings.debugCrosshairStyle == DebugCrosshairSetting.V1_12 && DebugCrosshair.isDebugCrosshairVisible(mc) && mc.field_71474_y.field_74320_O == 0) {
            DebugCrosshair.render(mc, new ScaledResolution(mc, mc.field_71443_c, mc.field_71440_d), ForgeFrameState.partialTicks);
            return;
        }
        boolean pushed = LegacyGuiScaleTransform.crosshair(LegacyWindows.current(), x, y);
        boolean firstPerson = mc.field_71474_y.field_74320_O == 0;
        float rawOpacity = firstPerson ? LegacyCrosshairConfig.crosshairOpacityFirstPerson : LegacyCrosshairConfig.crosshairOpacityThirdPerson;
        float opacity = Float.isFinite(rawOpacity) ? Math.max(0f, Math.min(100f, rawOpacity)) / 100f : 1f;
        boolean plain = firstPerson ? LegacyCrosshairConfig.removeCrosshairBlendingFirstPerson : LegacyCrosshairConfig.removeCrosshairBlendingThirdPerson;
        boolean style = plain || opacity != 1f;
        if (style) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
            GL14.glBlendFuncSeparate(plain ? GL11.GL_SRC_ALPHA : GL11.GL_ONE_MINUS_DST_COLOR,
                plain ? GL11.GL_ONE_MINUS_SRC_ALPHA : GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glColor4f(1f, 1f, 1f, opacity);
        }
        try { original.call(gui, x, y, u, v, width, height); }
        finally { if (style) GL11.glPopAttrib(); LegacyGuiScaleTransform.pop(pushed); }
    }

    @WrapOperation(method = "renderHUDText(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;func_78261_a(Ljava/lang/String;III)I"), remap = false)
    private int arcadium$debugText(FontRenderer font, String text, int x, int y, int color, Operation<Integer> original) {
        Minecraft mc = Minecraft.func_71410_x();
        var settings = OverflowAnimationsConfig.instance().screen;
        if (!mc.field_71474_y.field_74330_P || settings.legacyDebugScreen) return original.call(font, text, x, y, color);
        if (!settings.disableDebugHudBackground) Gui.func_73734_a(x - 1, y - 1, x + font.func_78256_a(text) + 1, y + font.field_78288_b - 1, -1873784752);
        return settings.debugHudTextShadow ? font.func_78261_a(text, x, y, color) : font.func_78276_b(text, x, y, color);
    }
}
