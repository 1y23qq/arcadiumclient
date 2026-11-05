// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ForgePose072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing", remap = false)
public abstract class ForgePose072BatchProbe {
    @Inject(method = "beginBatch", at = @At("TAIL"), remap = false)
    private void forgePose$batch(CallbackInfoReturnable<Boolean> callback) { ForgePose072Qa.batch(callback.getReturnValue()); }
}
