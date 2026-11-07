// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.runtime.qa.TerrainBuildEligibility073;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionCuller;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=OcclusionCuller.class,remap=false)
public abstract class Terrain073EligibilityProbe {
    @WrapOperation(method="process",at=@At(value="INVOKE",target="Lorg/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller$Visitor;visit(IIIIIIIZ)V",remap=false),remap=false)
    private void terrain073$visit(OcclusionCuller.Visitor receiver,int cell,int region,int local,int x,int y,int z,int compact,boolean eligible,Operation<Void> original,
                                 @Local(argsOnly=true)Viewport viewport,@Local(argsOnly=true)float distance,@Local(argsOnly=true)int frame){
        TerrainBuildEligibility073.visit(viewport,distance,frame,x,y,z,compact,eligible);original.call(receiver,cell,region,local,x,y,z,compact,eligible);
    }
}
