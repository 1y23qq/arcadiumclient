// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072Qa;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline",remap=false)
public abstract class CeraAdmission072AnalyticProbe {
    @WrapMethod(method="queueAnalytic",remap=false)
    private static int cera$texture(ModelPart[] parts,int mask,Identifier texture,int job,int template,int light,float age,Operation<Integer> original){
        int token=CeraAdmission072Qa.beginAnalytic(mask,texture);
        try{return original.call(parts,mask,texture,job,template,light,age);}finally{CeraAdmission072Qa.endAnalytic(token);}
    }
}
