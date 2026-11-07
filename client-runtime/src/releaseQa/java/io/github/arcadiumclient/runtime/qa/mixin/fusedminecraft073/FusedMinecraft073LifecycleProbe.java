// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=GpuModelTransforms.class,remap=false)
public abstract class FusedMinecraft073LifecycleProbe {
 @WrapMethod(method="close",remap=false)
 private static void fused073$close(Operation<Void> original){Object token=FusedMinecraft073Evidence.closing();try{original.call();}finally{FusedMinecraft073Evidence.closed(token);}}
}
