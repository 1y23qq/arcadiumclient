// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.redaction.mixin.client.legacy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import org.lwjgl.opengl.GL11;
import org.polyfrost.redaction.client.RedactionConfig;
import org.polyfrost.redaction.client.features.BlackBar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class GameGuiMixin_RenderBlackBar {
    @Shadow @Final private Minecraft minecraft;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;drawTexture(IIIIII)V"))
    private void redaction$hotbar(GameGui gui, int x, int y, int u, int v, int width, int height, Operation<Void> original) {
        if (RedactionConfig.INSTANCE.getBlackbar()) {
            if (u == 0 && v == 0 && width == 182 && height == 22) { BlackBar.INSTANCE.render(minecraft.player); return; }
            if (u == 0 && v == 22 && width == 24 && height == 22) return;
        }
        original.call(gui, x, y, u, v, width, height);
    }

    @Inject(method = "renderInventorySlot", at = @At("TAIL"))
    private void redaction$slotNumber(int slot, int x, int y, float delta, CallbackInfo callback) {
        if (!RedactionConfig.INSTANCE.getBlackbar() || !RedactionConfig.INSTANCE.getBlackbarSlotNumbers()) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try { GL11.glDisable(GL11.GL_DEPTH_TEST); minecraft.textRenderer.draw(String.valueOf(slot + 1), x, y, 0xFFFFFFFF); }
        finally { GL11.glPopAttrib(); }
    }
}
