// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.HypixelLabQa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class HypixelLabMixin {
    @Inject(method = "runGame", at = @At("HEAD"))
    private void hypixel$labFrameHead(CallbackInfo callback) { HypixelLabQa.frameHead(); }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hypixel$labTick(CallbackInfo callback) { HypixelLabQa.tick(); }

    @Inject(method = "runGame", at = @At("TAIL"))
    private void hypixel$labFrame(CallbackInfo callback) { HypixelLabQa.frame(); }
}
