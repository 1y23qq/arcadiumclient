// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.features.CustomCrosshairRenderer;
import io.github.arcadiumclient.runtime.client.features.LegacyGuiScaleTransform;
import io.github.arcadiumclient.runtime.legacy.LegacyWindows;
import io.github.arcadiumclient.runtime.legacy.LegacyCrosshairConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

@Mixin(GameGui.class)
public abstract class MixinGuiScaleHud {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;drawTexture(IIIIII)V"))
    private void arcadium$hudTextures(GameGui gui, int x, int y, int u, int v, int width, int height, Operation<Void> original) {
        var window = LegacyWindows.current();
        boolean crosshair = u == 0 && v == 0 && width == 16 && height == 16;
        if (crosshair && (!CustomCrosshairRenderer.visible(true) || CustomCrosshairRenderer.render())) return;
        boolean bar = height == 22 && (width == 182 || width == 24)
            || height == 5 && u == 0 && (v == 64 || v == 69 || v == 84 || v == 89);
        boolean pushed = crosshair ? LegacyGuiScaleTransform.crosshair(window, x, y)
            : bar && LegacyGuiScaleTransform.hotbar(window);
        boolean style = false;
        if (crosshair) {
            boolean first = Minecraft.getInstance().options.perspective == 0;
            float rawOpacity = first ? LegacyCrosshairConfig.crosshairOpacityFirstPerson : LegacyCrosshairConfig.crosshairOpacityThirdPerson;
            float opacity = Float.isFinite(rawOpacity) ? Math.max(0f, Math.min(100f, rawOpacity)) / 100f : 1f;
            boolean plain = first ? LegacyCrosshairConfig.removeCrosshairBlendingFirstPerson : LegacyCrosshairConfig.removeCrosshairBlendingThirdPerson;
            if (plain || opacity != 1f) {
                GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
                style = true;
                GL14.glBlendFuncSeparate(plain ? GL11.GL_SRC_ALPHA : GL11.GL_ONE_MINUS_DST_COLOR,
                    plain ? GL11.GL_ONE_MINUS_SRC_ALPHA : GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
                GL11.glColor4f(1f, 1f, 1f, opacity);
            }
        }
        try { original.call(gui, x, y, u, v, width, height); }
        finally { if (style) GL11.glPopAttrib(); LegacyGuiScaleTransform.pop(pushed); }
    }

    @WrapMethod(method = "renderStatusBars")
    private void arcadium$status(int width, int height, Operation<Void> original) {
        boolean pushed = LegacyGuiScaleTransform.hotbar(LegacyWindows.current());
        try { original.call(width, height); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }

    @WrapMethod(method = "renderInventorySlot")
    private void arcadium$slot(int index, int x, int y, float delta, Operation<Void> original) {
        boolean pushed = LegacyGuiScaleTransform.hotbar(LegacyWindows.current());
        try { original.call(index, x, y, delta); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }

    @WrapOperation(method = "render", slice = @Slice(to = @At(value = "FIELD", target = "Lnet/minecraft/client/network/handler/ClientPlayNetworkHandler;onlinePlayers:Ljava/util/List;", ordinal = 0)),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;III)I"))
    private int arcadium$itemName(TextRenderer font, String text, int x, int y, int color, Operation<Integer> original) {
        var window = LegacyWindows.current();
        boolean pushed = y >= window.getHeight() - 63 && y < window.getHeight() - 15 && LegacyGuiScaleTransform.hotbar(window);
        try { return original.call(font, text, x, y, color); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }
}
