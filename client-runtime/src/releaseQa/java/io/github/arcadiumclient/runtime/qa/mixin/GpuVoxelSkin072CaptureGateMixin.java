// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072QualityControls;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa", remap = false)
public abstract class GpuVoxelSkin072CaptureGateMixin {
    @Shadow @Final private static boolean BASELINE;
    @Shadow private static boolean effectiveSkin;
    @Shadow private static long phaseNativeVoxelStart, phaseAnalyticVoxelStart;
    @Shadow private static long counter(String type, String field) { throw new AssertionError(); }
    @Shadow private static boolean gpuVoxels() { throw new AssertionError(); }

    @Inject(method = "armCounters", at = @At("TAIL"), remap = false)
    private static void voxel072$armCapture(CallbackInfo callback) { GpuVoxelSkin072QualityControls.armCapture(); }

    @Inject(method = "requireVoxelPath", at = @At("TAIL"), remap = false)
    private static void voxel072$requireNativeCapture(CallbackInfo callback) {
        if (!BASELINE && effectiveSkin && gpuVoxels() && GpuVoxelSkin072QualityControls.captureRequested())
            GpuVoxelSkin072QualityControls.captureProof(counter("GpuVoxelSkinPipeline", "nativeParts") - phaseNativeVoxelStart,
                    counter("GpuVoxelSkinPipeline", "analyticParts") - phaseAnalyticVoxelStart,
                    counter("GpuVoxelSkinPipeline", "nativePartMask"));
    }
}
