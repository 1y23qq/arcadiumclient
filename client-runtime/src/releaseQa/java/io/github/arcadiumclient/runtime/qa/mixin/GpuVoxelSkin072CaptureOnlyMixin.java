// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072QualityControls;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline", remap = false)
public abstract class GpuVoxelSkin072CaptureOnlyMixin {
    @Inject(method = "queueAnalytic", at = @At("HEAD"), cancellable = true, remap = false)
    private static void voxel072$capturedPose(ModelPart[] parts, int mask, Identifier texture, int sourceJob, int sourceTemplate,
                                              int light, float age, CallbackInfoReturnable<Integer> callback) {
        if (GpuVoxelSkin072QualityControls.captureOnly()) callback.setReturnValue(0);
    }
    @Inject(method = "captureMesh", at = @At("RETURN"), remap = false)
    private static void voxel072$actualCapturedPart(Object mesh, float scale, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) GpuVoxelSkin072QualityControls.nativeCapture(mesh);
    }
}
