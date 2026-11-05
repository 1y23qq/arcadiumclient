// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("legacy-skin-layers")
@Mixin(targets = "org.polyfrost.skinlayers.render.CompiledMesh", remap = false)
public abstract class MixinGpuVoxelSkinMesh {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V", remap = true), remap = false)
    private void arcadium$voxelBatch(int displayList, Operation<Void> original, @Local(argsOnly = true) float scale) {
        if (!GpuVoxelSkinPipeline.captureMesh(this, scale)) original.call(displayList);
    }
    @Inject(method = "dispose", at = @At("HEAD"), remap = false)
    private void arcadium$releaseMesh(CallbackInfo callback) { GpuVoxelSkinPipeline.disposeMesh(this); }
}
