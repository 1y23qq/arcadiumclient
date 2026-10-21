// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.mixin;

import org.polyfrost.oneconfig.internal.OneConfig;
import io.github.arcadiumclient.forge1710.platform.ForgeFrameState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraftforge.client.GuiIngameForge")
public abstract class ForgeHudRenderMixin {
    @Inject(method = "func_73830_a(FZII)V", at = @At("HEAD"), remap = false)
    private void arcadium$frame(float partialTicks, boolean mouseGrabbed, int mouseX, int mouseY, CallbackInfo callback) {
        ForgeFrameState.partialTicks = partialTicks;
    }

    @Inject(method = "func_73830_a(FZII)V", at = @At("TAIL"), remap = false)
    private void arcadium$hud(float partialTicks, boolean mouseGrabbed, int mouseX, int mouseY, CallbackInfo callback) {
        OneConfig.render();
    }
}
