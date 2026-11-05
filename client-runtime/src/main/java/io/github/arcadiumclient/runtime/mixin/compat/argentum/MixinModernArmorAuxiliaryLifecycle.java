// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorAuxiliary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer",remap=false)
public abstract class MixinModernArmorAuxiliaryLifecycle {
    @Inject(method={"close","reload"},at=@At("HEAD"),remap=false)
    private void arcadium$closeModernBeforeMain(@Coerce Object commands,CallbackInfo callback){ModernArmorAuxiliary.beforeMainClose(this,commands);}
    @Inject(method="flush",at=@At("HEAD"),remap=false)
    private void arcadium$drainModernBeforeMain(@Coerce Object commands,CallbackInfoReturnable<?> callback){ModernArmorAuxiliary.beforeMainFlush(this,commands);}
}
