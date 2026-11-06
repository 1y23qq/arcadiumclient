// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.qa.CeraResidentBody072Evidence;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuResidentBodyDraw",remap=false)
public abstract class CeraAdmission072ResidentPaletteProbe {
    @WrapMethod(method="draw")
    private void cera$resident(IntBuffer payload,int count,int uv,int profiles,int capacity,@org.spongepowered.asm.mixin.injection.Coerce Object palette,Operation<Void> original){
        int token=CeraResidentBody072Evidence.beforeDraw(payload,count,profiles,capacity,palette);
        try{original.call(payload,count,uv,profiles,capacity,palette);}finally{CeraResidentBody072Evidence.afterDraw(token);}
    }
    @WrapOperation(method="draw",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL31;glDrawArraysInstanced(IIII)V",remap=false),remap=false)
    private void cera$actualResident(int mode,int first,int vertices,int instances,Operation<Void> original){original.call(mode,first,vertices,instances);CeraResidentBody072Evidence.actualDraw(mode,first,vertices,instances);}
}
