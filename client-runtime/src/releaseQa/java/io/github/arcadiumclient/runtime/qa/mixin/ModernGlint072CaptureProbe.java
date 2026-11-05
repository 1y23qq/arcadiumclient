// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ModernGlint072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityCapture", remap = false)
public abstract class ModernGlint072CaptureProbe {
    @Inject(method = "beginEntity", at = @At("HEAD"), remap = false)
    private void modern$camera(CallbackInfo callback) { ModernGlint072Qa.captureCamera(); }

    @Inject(method="beginLayer",at=@At("HEAD"),remap=false)
    private void modern$layer(@Coerce Object layer,@Coerce Object entity,CallbackInfoReturnable<Boolean> callback){ModernGlint072Qa.layerAttempt(layer,entity);}
}
