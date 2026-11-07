// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073VoxelStageEvidence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer",remap=false)
public abstract class PlayerFeatures073VoxelShadowDrawProbe {
    @WrapOperation(method="draw(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;II)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/ARBDrawInstanced;glDrawArraysInstancedARB(IIII)V"),remap=false)
    private void features073$shadowDraw(int primitive,int first,int vertices,int instances,Operation<Void> original){int token=PlayerFeatures073VoxelStageEvidence.beforeDeferred(primitive,first,vertices,instances);boolean returned=false;try{original.call(primitive,first,vertices,instances);returned=true;}finally{PlayerFeatures073VoxelStageEvidence.after(token,returned);}}
}
