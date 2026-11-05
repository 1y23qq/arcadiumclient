// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072InterpolationProbe;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferUploader.class)
public abstract class GpuVoxelSkin072InterpolationClientProbe {
    @Inject(method="end",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL11;glDrawArrays(III)V",remap=false),cancellable=true,require=0)
    private void voxel072$validateClientMemory(BufferBuilder builder,CallbackInfo callback){if(!GpuVoxelSkin072InterpolationProbe.validateOriginal(builder))callback.cancel();}
}
