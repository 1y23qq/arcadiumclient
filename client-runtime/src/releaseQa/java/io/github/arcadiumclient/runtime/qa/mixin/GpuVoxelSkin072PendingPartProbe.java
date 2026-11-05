// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072PendingPartEvidence;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinAdmission",remap=false)
public abstract class GpuVoxelSkin072PendingPartProbe {
    @Shadow(remap=false) abstract String reason();
    @WrapMethod(method="admit",remap=false)
    private boolean voxel072$pendingBody(@Coerce Object api,Object main,Identifier texture,ModelPart[] parts,int mask,Operation<Boolean> original){
        return GpuVoxelSkin072PendingPartEvidence.run(parts,mask,()->original.call(api,main,texture,parts,mask),this::reason);
    }
}
