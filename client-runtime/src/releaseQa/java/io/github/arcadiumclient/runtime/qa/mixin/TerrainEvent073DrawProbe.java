// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainEvent073Evidence;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentScene$Boundary",remap=false)
public abstract class TerrainEvent073DrawProbe {
 @WrapOperation(method="gpu",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL46;glMultiDrawElementsIndirectCount(IIJJII)V"))
 private void eventDraw(int mode,int type,long indirect,long count,int maximum,int stride,Operation<Void> original){Object token=TerrainEvent073Evidence.beforeDraw(this,mode,type,indirect,count,maximum,stride);try{original.call(mode,type,indirect,count,maximum,stride);}finally{TerrainEvent073Evidence.afterDraw(token);}}
 @WrapOperation(method="gpu",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/ARBIndirectParameters;glMultiDrawElementsIndirectCountARB(IIJJII)V"))
 private void eventArbDraw(int mode,int type,long indirect,long count,int maximum,int stride,Operation<Void> original){Object token=TerrainEvent073Evidence.beforeDraw(this,mode,type,indirect,count,maximum,stride);try{original.call(mode,type,indirect,count,maximum,stride);}finally{TerrainEvent073Evidence.afterDraw(token);}}
}
