// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072TextureCopyEvidence;
import java.nio.IntBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinDraw",remap=false)
public abstract class GpuVoxelSkin072TextureCopyProbe {
    @Inject(method="draw",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL43;glMultiDrawArraysIndirect(IJII)V"),remap=false,require=1)
    private void voxel072$nativeCopy(CommandList device,IntBuffer payload,int count,CallbackInfo callback){GpuVoxelSkin072TextureCopyEvidence.beforeDraw(this,count);}
}
