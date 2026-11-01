// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.NextRenderingQa;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuNameTags",remap=false)
public abstract class NextNameFaultProbe {
    @Shadow private void fail(Throwable error){throw new AssertionError();}
    @Inject(method="draw",at=@At("HEAD"),remap=false)
    private void next$fail(BufferBuilder buffer,CallbackInfoReturnable<Boolean> callback){
        if(NextRenderingQa.enabled()&&NextRenderingQa.nameFaultRequested){NextRenderingQa.nameFaultRequested=false;NextRenderingQa.nameFaultInjected=true;fail(new IllegalStateException("QA: simulated name renderer failure"));}
    }
}
