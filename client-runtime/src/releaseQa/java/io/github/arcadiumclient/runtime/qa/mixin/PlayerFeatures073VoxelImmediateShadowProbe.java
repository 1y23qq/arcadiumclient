// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073VoxelStageEvidence;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class PlayerFeatures073VoxelImmediateShadowProbe {
    @WrapMethod(method="renderShadow")
    private void features073$shadowScope(Entity entity,double x,double y,double z,float a,float b,Operation<Void> original){int token=PlayerFeatures073VoxelStageEvidence.immediateEnter(entity);try{original.call(entity,x,y,z,a,b);}finally{PlayerFeatures073VoxelStageEvidence.immediateExit(token);}}
    @WrapOperation(method="renderShadow",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
    private void features073$shadowDraw(Tesselator tesselator,Operation<Void> original){int token=PlayerFeatures073VoxelStageEvidence.beforeImmediate();boolean returned=false;try{original.call(tesselator);returned=true;}finally{PlayerFeatures073VoxelStageEvidence.after(token,returned);}}
}
