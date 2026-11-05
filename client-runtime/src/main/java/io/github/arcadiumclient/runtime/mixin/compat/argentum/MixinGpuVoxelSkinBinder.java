// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("legacy-skin-layers")
@Mixin(targets = "org.polyfrost.skinlayers.PlayerLayerBinder", remap = false)
public abstract class MixinGpuVoxelSkinBinder {
    @Inject(method = "bind", at = @At("TAIL"), remap = false)
    private static void arcadium$observeVoxelOffsets(@Coerce Object model, @Coerce Object player, boolean slim, CallbackInfo callback) {
        GpuVoxelSkinPipeline.observeModel(model);
    }
}
