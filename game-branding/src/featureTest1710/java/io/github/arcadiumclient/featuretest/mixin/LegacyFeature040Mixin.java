// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.Legacy040Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class LegacyFeature040Mixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadium$testTick(CallbackInfo callback) { Legacy040Qa.tick(); }
    @Inject(method = "runGame", at = @At("TAIL"))
    private void arcadium$testFrame(CallbackInfo callback) { Legacy040Qa.afterFrame(); }
}
