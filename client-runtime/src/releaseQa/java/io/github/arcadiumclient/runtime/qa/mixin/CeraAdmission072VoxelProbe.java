// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets="org.polyfrost.skinlayers.render.CompiledMesh",remap=false)
public abstract class CeraAdmission072VoxelProbe {
    @WrapMethod(method="render",remap=false)
    private void cera$voxel(float scale,Operation<Void> original){CeraAdmission072Qa.voxel(true);try{original.call(scale);}finally{CeraAdmission072Qa.voxel(false);}}
}
