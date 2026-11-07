// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073VoxelStageEvidence;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="dev.rdh.argentum.impl.render.entity.EntityShadowBatch",remap=false)
public abstract class PlayerFeatures073VoxelShadowBatchProbe {
    @WrapMethod(method="flush",remap=false)
    private void features073$shadowScope(CommandList commands,Operation<Void> original){int token=PlayerFeatures073VoxelStageEvidence.deferredEnter(this);try{original.call(commands);}finally{PlayerFeatures073VoxelStageEvidence.deferredExit(token);}}
    @Inject(method="record",at=@At("RETURN"),remap=false)
    private void features073$shadowRecord(@Coerce Object world,@Coerce Object entity,double x,double y,double z,float first,float second,float third,CallbackInfoReturnable<Boolean> result){PlayerFeatures073VoxelStageEvidence.record(this,entity,result.getReturnValueZ());}
}
