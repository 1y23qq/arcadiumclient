// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ForgePose072Qa;
import net.minecraft.client.render.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityCapture", remap = false)
public abstract class ForgePose072CaptureProbe {
    @Inject(method = "beginEntity", at = @At("TAIL"), remap = false)
    private void forgePose$begin(@Coerce Object model, @Coerce Object texture, boolean player, boolean fixed, int light,
            float time, float red, float green, float blue, float alpha, CallbackInfo callback) { ForgePose072Qa.captureBegin(this, player); }
    @Inject(method = "beginModel", at = @At("TAIL"), remap = false)
    private void forgePose$model(CallbackInfo callback) { ForgePose072Qa.captureModel(this); }
    @Inject(method = "recordPart", at = @At("HEAD"), remap = false)
    private void forgePose$part(ModelPart part, float scale, CallbackInfo callback) { ForgePose072Qa.originalPart(); }
    @Inject(method = "beginLayer", at = @At("TAIL"), remap = false)
    private void forgePose$layer(Object layer, @Coerce Object entity, CallbackInfoReturnable<Boolean> callback) {
        ForgePose072Qa.layer(this, layer, callback.getReturnValue());
    }
}
