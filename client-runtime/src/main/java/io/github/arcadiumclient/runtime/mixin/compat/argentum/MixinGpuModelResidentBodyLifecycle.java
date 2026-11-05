// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuResidentBodies;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer",remap=false)
public abstract class MixinGpuModelResidentBodyLifecycle {
    @Inject(method="close",at=@At("HEAD"),remap=false)
    private void arcadium$bodyClose(@Coerce Object commands,CallbackInfo callback){GpuResidentBodies.beforeMainClose(this,commands);}
}
