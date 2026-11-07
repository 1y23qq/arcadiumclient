// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=GpuModelTransforms.class,remap=false)
public abstract class FusedMinecraft073TransformProbe {
 @WrapMethod(method="expand",remap=false)
 private static void fused073$expand(int buffer,IntBuffer payload,Operation<Void> original){Object token=FusedMinecraft073Evidence.expandBegin(buffer,payload);boolean returned=false;try{original.call(buffer,payload);returned=true;}finally{FusedMinecraft073Evidence.expandEnd(token,returned);}}
 @WrapOperation(method="expand",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL43;glDispatchCompute(III)V"),remap=false)
 private static void fused073$dispatch(int x,int y,int z,Operation<Void> original){boolean returned=false;try{original.call(x,y,z);returned=true;}finally{FusedMinecraft073Evidence.actualDispatch(returned,x,y,z);}}
}
