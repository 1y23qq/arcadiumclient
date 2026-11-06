// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuResidentBodyShaderState;
import java.util.function.Function;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(value=GlProgram.class,remap=false)
public abstract class MixinGpuModelResidentBodyProgram {
    @Inject(method="<init>",at=@At("TAIL"),remap=false)
    private void arcadium$bodyCreated(int handle,Function<?,?> factory,CallbackInfo callback){GpuResidentBodyShaderState.programCreated(handle);}
    @Inject(method="destroyInternal",at=@At("HEAD"),remap=false)
    private void arcadium$bodyDeleted(CallbackInfo callback){GpuResidentBodyShaderState.programDeleted(((GlProgram<?>)(Object)this).handle());}
}
