// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import io.github.arcadiumclient.runtime.client.features.GuiScaling;
import io.github.arcadiumclient.runtime.client.features.CustomCrosshair;
import io.github.arcadiumclient.runtime.client.features.LegacyGuiScaleTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameGui.class)
public abstract class MixinGuiScaleHud {
    @WrapMethod(method = "renderHotbar")
    private void arcadium$hotbar(Window window, float tickDelta, Operation<Void> original) {
        boolean pushed = LegacyGuiScaleTransform.hotbar(window);
        try { original.call(window, tickDelta); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }

    @WrapMethod(method = {"renderStatusBars", "renderSelectedItemName"})
    private void arcadium$hotbarStatus(Window window, Operation<Void> original) {
        boolean pushed = LegacyGuiScaleTransform.hotbar(window);
        try { original.call(window); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }

    @WrapMethod(method = {"renderXpBar", "renderJumpBar"})
    private void arcadium$hotbarMeter(Window window, int x, Operation<Void> original) {
        boolean pushed = LegacyGuiScaleTransform.hotbar(window);
        try { original.call(window, x); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }

    @WrapOperation(method = "render(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;drawTexture(IIIIII)V"))
    private void arcadium$vanillaCrosshair(GameGui gui, int x, int y, int u, int v, int width, int height, Operation<Void> original) {
        if (!GuiScaling.enabled() || CustomCrosshair.isActive()) {
            original.call(gui, x, y, u, v, width, height);
            return;
        }
        boolean pushed = LegacyGuiScaleTransform.crosshair(new Window(Minecraft.getInstance()), x, y);
        try { original.call(gui, x, y, u, v, width, height); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }
}
