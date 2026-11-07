// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073VoxelEvidence;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073VoxelProjectionControl;
import io.github.arcadiumclient.runtime.qa.P3PhysicalOrdinary073Evidence;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinDraw",remap=false)
public abstract class PlayerFeatures073VoxelDrawProbe {
    @WrapMethod(method="draw",remap=false)
    private void features073$upload(@Coerce Object commands,IntBuffer payload,int count,Operation<Void> original){P3PhysicalOrdinary073Evidence.upload(this,payload,count);PlayerFeatures073VoxelProjectionControl.prepare(this,payload,count);PlayerFeatures073VoxelEvidence.upload(this,payload,count);try{original.call(commands,payload,count);}finally{PlayerFeatures073VoxelProjectionControl.uploadEnded(this);}}
    @WrapOperation(method="draw",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL43;glMultiDrawArraysIndirect(IJII)V"),remap=false)
    private void features073$mdi(int mode,long offset,int count,int stride,Operation<Void> original){int token=PlayerFeatures073VoxelProjectionControl.beforeDraw(mode,offset,count,stride);boolean returned=false;try{original.call(mode,offset,count,stride);returned=true;PlayerFeatures073VoxelProjectionControl.drawReturned(token,true);PlayerFeatures073VoxelEvidence.mdi(mode,offset,count,stride);}finally{if(!returned)PlayerFeatures073VoxelProjectionControl.drawReturned(token,false);PlayerFeatures073VoxelProjectionControl.afterDraw(token);}}
    @WrapOperation(method="drawPhysical",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL43;glMultiDrawArraysIndirect(IJII)V"),remap=false)
    private void p3$ordinaryPhysicalMdi(int mode,long offset,int count,int stride,Operation<Void> original){int token=P3PhysicalOrdinary073Evidence.observeBefore(this,mode,offset,count,stride);boolean returned=false;try{original.call(mode,offset,count,stride);returned=true;}finally{P3PhysicalOrdinary073Evidence.observeAfter(token,returned,mode,offset,count,stride);}}
}
