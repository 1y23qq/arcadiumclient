// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import io.github.arcadiumclient.runtime.client.features.GuiScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class MixinGuiScaleGameRenderer {
    @WrapOperation(
        method = "render(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V")
    )
    private void arcadium$containerProjection(Screen screen, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        if (!GuiScaling.isContainer(screen) || !GuiScaling.enabled()) {
            original.call(screen, mouseX, mouseY, tickDelta);
            return;
        }
        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, screen.width, screen.height, 0, 1000, 3000);
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translatef(0, 0, -2000);
        try {
            original.call(screen, GuiScaling.mouseX(screen), GuiScaling.mouseY(screen), tickDelta);
        } finally {
            GlStateManager.matrixMode(5888);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5889);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
        }
    }
}
