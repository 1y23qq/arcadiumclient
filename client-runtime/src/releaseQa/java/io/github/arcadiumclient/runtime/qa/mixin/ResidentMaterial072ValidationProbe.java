// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ResidentMaterial072Evidence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuResidentBodyAccess",remap=false)
public abstract class ResidentMaterial072ValidationProbe {
    @Inject(method="material",at=@At("HEAD"),remap=false)
    private void material072$begin(Object main,Object batch,@Coerce Object texture,CallbackInfoReturnable<?> callback){ResidentMaterial072Evidence.begin();}
    @Inject(method="material",at=@At("RETURN"),remap=false)
    private void material072$end(Object main,Object batch,@Coerce Object texture,CallbackInfoReturnable<?> callback){ResidentMaterial072Evidence.end(callback.getReturnValue());}
}
