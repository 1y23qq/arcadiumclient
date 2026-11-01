// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.api.render.instancing.InstanceDataBuffer", remap = false)
public abstract class MixinGpuModelSort {
    @Shadow private int[] data;
    @Shadow private int count;
    @Shadow @Final private int stride;
    @Inject(method = "sortBackToFront", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$deferSort(CallbackInfo callback) {
        if (stride == GpuModelTransforms.STRIDE && GpuModelTransforms.deferSort(data,count)) callback.cancel();
    }
}
