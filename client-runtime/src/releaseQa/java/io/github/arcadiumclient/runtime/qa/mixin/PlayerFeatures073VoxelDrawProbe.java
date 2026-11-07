// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073VoxelEvidence;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinDraw",remap=false)
public abstract class PlayerFeatures073VoxelDrawProbe {
    @WrapMethod(method="draw",remap=false)
    private void features073$upload(@Coerce Object commands,IntBuffer payload,int count,Operation<Void> original){PlayerFeatures073VoxelEvidence.upload(this,payload,count);original.call(commands,payload,count);}
    @WrapOperation(method="draw",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL43;glMultiDrawArraysIndirect(IJII)V"),remap=false)
    private void features073$mdi(int mode,long offset,int count,int stride,Operation<Void> original){original.call(mode,offset,count,stride);PlayerFeatures073VoxelEvidence.mdi(mode,offset,count,stride);}
}
