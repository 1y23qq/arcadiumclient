// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="org.polyfrost.overflowanimations.legacy.firstperson.LegacyFirstPerson",remap=false)
public abstract class PlayerFeatures073LegacyTransformProbe {
    @Inject(method="applyFirstPersonTransform",at=@At("RETURN"),remap=false)
    private static void features073$legacy(@Coerce Object stack,@Coerce Object model,CallbackInfoReturnable<Boolean> callback){PlayerFeatures073Qa.legacyTransform(Boolean.TRUE.equals(callback.getReturnValue()));}
}
