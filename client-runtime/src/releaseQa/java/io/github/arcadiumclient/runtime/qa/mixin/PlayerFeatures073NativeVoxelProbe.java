// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073NativeVoxelEvidence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets="org.polyfrost.skinlayers.render.CompiledMesh",remap=false)
public abstract class PlayerFeatures073NativeVoxelProbe {
    @WrapMethod(method="render",remap=false)
    private void features073$nativeScope(float scale,Operation<Void> original){int token=PlayerFeatures073NativeVoxelEvidence.enter(this,scale);try{original.call(scale);}finally{PlayerFeatures073NativeVoxelEvidence.leave(token);}}
    @WrapOperation(method="emit",at=@At(value="INVOKE",target="Lnet/minecraft/unmapped/C_96603444;m_70070273()V"),remap=false)
    private void features073$nativeInput(@Coerce Object tesselator,Operation<Void> original,@Local(argsOnly=true) float scale){PlayerFeatures073NativeVoxelEvidence.emitted(this,scale,tesselator);original.call(tesselator);}
}
