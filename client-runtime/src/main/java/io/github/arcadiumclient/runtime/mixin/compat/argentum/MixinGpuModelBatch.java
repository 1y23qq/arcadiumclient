// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer", remap = false)
public abstract class MixinGpuModelBatch {
    @Inject(method = "beginBatch", at = @At("HEAD"), remap = false)
    private void arcadium$beginJobs(CallbackInfoReturnable<Boolean> callback) {
        if(!GpuVoxelSkinPipeline.isAuxiliaryBackend(this))GpuModelTransforms.beginBatch(this);
    }
    @Inject(method = "beginBatch", at = @At("RETURN"), remap = false)
    private void arcadium$prepareVoxels(CallbackInfoReturnable<Boolean> callback) {
        GpuVoxelSkinPipeline.prepareMainBatch(this,callback.getReturnValueZ());
    }
    @Inject(method = "flush", at = @At("HEAD"), remap = false)
    private void arcadium$flushPendingVoxels(@Coerce Object commands,CallbackInfoReturnable<?> callback) {
        GpuVoxelSkinPipeline.beforeMainFlush(this,commands);
    }
}
