// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline;
import io.github.arcadiumclient.runtime.qa.UrgentCape072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GpuPlayerPipeline.class, remap = false)
public abstract class UrgentCape072ResetProbe {
    @Inject(method = "reset", at = @At("HEAD"), remap = false)
    private static void cape072$reset(CallbackInfo callback) { UrgentCape072Qa.resetObserved(); }
}
