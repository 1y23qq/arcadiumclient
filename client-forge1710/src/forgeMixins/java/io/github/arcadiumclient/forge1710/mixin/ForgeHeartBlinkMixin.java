// SPDX-License-Identifier: MIT
// Adapted from BetterHurtCam. Copyright (c) 2022 uku.
package io.github.arcadiumclient.forge1710.mixin;

import net.uku3lig.betterhurtcam.BetterHurtCam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraftforge.client.GuiIngameForge", priority = 900)
public abstract class ForgeHeartBlinkMixin {
    @ModifyVariable(method = "renderHealth(II)V", at = @At("STORE"), index = 3, remap = false)
    private boolean arcadium$heartBlink(boolean original) {
        return BetterHurtCam.isHeartBlinkEnabled() && original;
    }
}
