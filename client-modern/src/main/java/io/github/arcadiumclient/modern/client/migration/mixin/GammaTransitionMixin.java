// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.modern.client.migration.GammaTransitionGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.sjouwer.gammautils.GammaManager$1", remap = false)
public class GammaTransitionMixin {
    @Unique private long arcadium$generation;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void arcadium$captureOwner(CallbackInfo callback) { arcadium$generation = GammaTransitionGuard.token(); }
    @WrapMethod(method = "run")
    private void arcadium$keepProfileOwner(Operation<Void> original) { GammaTransitionGuard.run(arcadium$generation, () -> original.call()); }
}
