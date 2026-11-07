// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073NativeVoxelEvidence;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(BufferUploader.class)
public abstract class PlayerFeatures073NativeVoxelArrayProbe {
    @WrapOperation(method="end",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL11;glDrawArrays(III)V",remap=false))
    private void features073$nativeArray(int primitive,int first,int count,Operation<Void> original,@Local(argsOnly=true) BufferBuilder builder){PlayerFeatures073NativeVoxelEvidence.nativeArray(builder,primitive,first,count);original.call(primitive,first,count);}
}
