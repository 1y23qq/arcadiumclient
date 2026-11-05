// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ForgePose072Qa;
import net.minecraft.client.render.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelPart.class, priority = 2000)
public abstract class ForgePose072PartReference {
    @Inject(method = "render", at = @At("HEAD"))
    private void forgePose$reference(float scale, CallbackInfo callback) {
        ForgePose072Qa.glintPart((ModelPart)(Object)this);
    }
}
