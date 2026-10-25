// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat.polyhitbox;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.modern.client.features.AnimationsConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("polyhitbox")
@Mixin(targets = "org.polyfrost.polyhitbox.config.HitboxConfig", remap = false)
public abstract class MixinBadlionHitboxStyle {
    @Inject(method = {"getShowEyeHeight", "getShowViewRay"}, at = @At("RETURN"), cancellable = true, remap = false)
    private void arcadium$oldStyle(CallbackInfoReturnable<Boolean> cir) {
        var settings = AnimationsConfig.badlion();
        if (settings != null && settings.hitBox()) cir.setReturnValue(false);
    }
}
