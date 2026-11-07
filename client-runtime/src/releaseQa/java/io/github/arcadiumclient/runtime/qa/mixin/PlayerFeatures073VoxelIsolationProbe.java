// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073VoxelEvidence;
import net.minecraft.resource.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinRenderer",remap=false)
public abstract class PlayerFeatures073VoxelIsolationProbe {
    @WrapOperation(method="capture",at=@At(value="INVOKE",target="Lio/github/arcadiumclient/runtime/client/optimizations/GpuModelTransforms;ready()Z"),remap=false)
    private boolean features073$affine(Operation<Boolean> original){return PlayerFeatures073VoxelEvidence.affine()?false:original.call();}
    @WrapMethod(method="submit",remap=false)
    private boolean features073$submit(@Coerce Object state,@Coerce Object geometry,Object backend,Identifier texture,@Coerce Object pass,Matrix4f matrix,int light,Vector4fc color,float time,Vector4fc overlay,Object capture,Operation<Boolean> original){int token=PlayerFeatures073VoxelEvidence.submit(state,geometry,texture,pass,matrix,light,color,time,overlay,capture);boolean accepted=false;try{return accepted=original.call(state,geometry,backend,texture,pass,matrix,light,color,time,overlay,capture);}finally{PlayerFeatures073VoxelEvidence.submitted(token,accepted);}}
}
